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
 * <p>Concurrency defence (the week-5 lab's three layers):</p>
 * <ol>
 *   <li><b>Fail fast</b> — patient/appointment existence, time rules, and a
 *   fail-fast overlap scan all run before any write, so callers get clear errors;</li>
 *   <li><b>Atomic check-and-write</b> — {@code insertIfNoOverlap} /
 *   {@code updateIfNoOverlap} test the overlap and write in one database statement;
 *   zero affected rows means a racing request won the slot;</li>
 *   <li><b>Constraint backstop</b> — {@code uk_appointment_patient_start} rejects a
 *   same-start double booking, which the service translates into the same conflict
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
     * Fail-fast overlap scan (layer 1): clear errors without touching the write path.
     * The atomic statements (layer 2) still guard the write itself when two requests
     * pass this check at the same time.
     */
    private void rejectOverlap(long patientId, LocalDateTime startAt, LocalDateTime endAt, Long excludeId) {
        List<Appointment> existing = appointments.findByPatientIdOrderByStartAtAsc(patientId);
        boolean overlaps = existing.stream()
                .filter(other -> excludeId == null || !Objects.equals(other.getId(), excludeId))
                .anyMatch(other -> other.getStartAt().isBefore(endAt) && other.getEndAt().isAfter(startAt));
        if (overlaps) {
            throw new AppointmentConflictException(patientId, startAt);
        }
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
