package nz.ac.aut.comp713.clinic.service;

import nz.ac.aut.comp713.clinic.controller.AppointmentRequest;
import nz.ac.aut.comp713.clinic.controller.AppointmentResponse;
import nz.ac.aut.comp713.clinic.model.Appointment;
import nz.ac.aut.comp713.clinic.model.Patient;
import nz.ac.aut.comp713.clinic.repository.AppointmentRepository;
import nz.ac.aut.comp713.clinic.repository.PatientRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * Appointment use cases: {@code bookAppointment}, {@code getAppointment},
 * {@code rescheduleAppointment}, {@code cancelAppointment}.
 *
 * <p>Concurrency defence (layered — the week-5 lab's lesson that "a transaction is
 * atomic, but it is not automatically concurrency-safe"):</p>
 * <ol>
 *   <li><b>Serialise per patient</b> — every booking change first takes a
 *   pessimistic row lock on the patient, so concurrent changes for one patient run
 *   one at a time (H2 has no range-exclusion constraint, so an atomic statement
 *   alone cannot stop two overlapping ranges from racing past their checks);</li>
 *   <li><b>Fail fast</b> — existence checks, time rules, and the overlap scan run
 *   before any write, so callers get clear errors;</li>
 *   <li><b>Atomic check-and-write</b> — {@code insertIfNoOverlap} /
 *   {@code updateIfNoOverlap} test the overlap and write in one database statement;
 *   zero affected rows means the slot was taken;</li>
 *   <li><b>Constraint backstop</b> — {@code uk_appointment_patient_start} rejects
 *   an identical-start double booking, translated into the same conflict
 *   error.</li>
 * </ol>
 */
@Service
public class AppointmentService {

    static final LocalTime OPENING_TIME = LocalTime.of(9, 0);
    static final LocalTime CLOSING_TIME = LocalTime.of(17, 0);

    private final PatientRepository patients;
    private final AppointmentRepository appointments;

    public AppointmentService(PatientRepository patients, AppointmentRepository appointments) {
        this.patients = patients;
        this.appointments = appointments;
    }

    /** Books a new appointment slot for a patient. */
    @Transactional
    public AppointmentResponse bookAppointment(long patientId, AppointmentRequest request) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId));

        LocalDateTime startAt = request.startAt();
        LocalDateTime endAt = endOf(request);
        validateSlot(startAt, endAt);
        rejectOverlap(patientId, startAt, endAt, null);

        int rows;
        try {
            rows = appointments.insertIfNoOverlap(patientId, startAt, endAt,
                    emptyToNull(request.reason()), LocalDateTime.now());
        } catch (DataIntegrityViolationException ex) {
            // Layer 3: uk_appointment_patient_start settled a race between requests.
            throw new AppointmentConflictException(patientId, startAt);
        }
        if (rows == 0) {
            // Layer 2: a racing request claimed an overlapping slot first.
            throw new AppointmentConflictException(patientId, startAt);
        }
        // Read back the row to obtain the generated id for the response/Location.
        Appointment booked = appointments.findByPatientIdAndStartAt(patientId, startAt).orElseThrow();
        return new AppointmentResponse(booked.getId(), patientId, booked.getStartAt(),
                booked.getEndAt(), booked.getReason());
    }

    /** One appointment by id. */
    @Transactional(readOnly = true)
    public AppointmentResponse getAppointment(long id) {
        return responseOf(appointments.findById(id)
                .orElseThrow(() -> new AppointmentNotFoundException(id)));
    }

    /**
     * Moves an appointment to a new slot (and updates its reason). The booking being
     * moved is excluded from the overlap check, so keeping its own slot is allowed.
     */
    @Transactional
    public AppointmentResponse rescheduleAppointment(long id, AppointmentRequest request) {
        Appointment appointment = appointments.findById(id)
                .orElseThrow(() -> new AppointmentNotFoundException(id));

        LocalDateTime startAt = request.startAt();
        LocalDateTime endAt = endOf(request);
        validateSlot(startAt, endAt);
        rejectOverlap(appointment.getPatientId(), startAt, endAt, id);

        int rows;
        try {
            rows = appointments.updateIfNoOverlap(id, appointment.getPatientId(), startAt, endAt,
                    emptyToNull(request.reason()));
        } catch (DataIntegrityViolationException ex) {
            throw new AppointmentConflictException(appointment.getPatientId(), startAt);
        }
        if (rows == 0) {
            throw new AppointmentConflictException(appointment.getPatientId(), startAt);
        }
        // The response is built from the values just written: re-reading the entity
        // in this transaction could return the stale first-level-cache copy, since
        // the atomic statement ran over JDBC outside the persistence context.
        return new AppointmentResponse(id, appointment.getPatientId(), startAt, endAt,
                emptyToNull(request.reason()));
    }

    /** Cancels (deletes) an appointment. */
    @Transactional
    public void cancelAppointment(long id) {
        Appointment appointment = appointments.findById(id)
                .orElseThrow(() -> new AppointmentNotFoundException(id));
        appointments.delete(appointment);
    }

    // --- scheduling rules ---

    private void validateSlot(LocalDateTime startAt, LocalDateTime endAt) {
        if (!startAt.isAfter(LocalDateTime.now())) {
            throw new InvalidAppointmentTimeException("The appointment must start in the future");
        }
        if (!endAt.toLocalDate().equals(startAt.toLocalDate())) {
            throw new InvalidAppointmentTimeException("The appointment must start and end on the same day");
        }
        DayOfWeek day = startAt.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw new InvalidAppointmentTimeException("The clinic is open Monday to Friday");
        }
        if (startAt.toLocalTime().isBefore(OPENING_TIME) || endAt.toLocalTime().isAfter(CLOSING_TIME)) {
            throw new InvalidAppointmentTimeException(
                    "The appointment must fit within the clinic's opening hours (09:00 to 17:00)");
        }
    }

    /**
     * Takes the per-patient serialisation lock (defence layer 1) and then runs the
     * fail-fast overlap scan (layer 2) — clear errors without touching the write
     * path. The atomic statements (layer 3) still guard the write itself.
     */
    private void rejectOverlap(long patientId, LocalDateTime startAt, LocalDateTime endAt, Long excludeId) {
        lockPatient(patientId);
        List<Appointment> existing = appointments.findByPatientIdOrderByStartAtAsc(patientId);
        boolean overlaps = existing.stream()
                .filter(other -> excludeId == null || !Objects.equals(other.getId(), excludeId))
                .anyMatch(other -> other.getStartAt().isBefore(endAt) && other.getEndAt().isAfter(startAt));
        if (overlaps) {
            throw new AppointmentConflictException(patientId, startAt);
        }
    }

    /**
     * Serialises concurrent booking changes for one patient: the pessimistic row
     * lock is held until the transaction commits, so the overlap check and write
     * cannot interleave with another booking for the same patient. Locking is
     * per-patient, so different patients never contend (the no-overlap rule is
     * per patient too).
     */
    private void lockPatient(long patientId) {
        // Executed for its locking side effect; existence is checked by the caller.
        patients.findByIdForUpdate(patientId);
    }

    private static LocalDateTime endOf(AppointmentRequest request) {
        return request.startAt().plusMinutes(request.durationMinutes());
    }

    private AppointmentResponse responseOf(Appointment appointment) {
        return new AppointmentResponse(appointment.getId(), appointment.getPatientId(),
                appointment.getStartAt(), appointment.getEndAt(), appointment.getReason());
    }

    private static String emptyToNull(String value) {
        String trimmed = value == null ? null : value.trim();
        return (trimmed == null || trimmed.isEmpty()) ? null : trimmed;
    }
}
