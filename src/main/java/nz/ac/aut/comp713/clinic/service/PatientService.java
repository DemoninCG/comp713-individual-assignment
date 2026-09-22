package nz.ac.aut.comp713.clinic.service;

import nz.ac.aut.comp713.clinic.controller.AppointmentResponse;
import nz.ac.aut.comp713.clinic.controller.PatientDetailResponse;
import nz.ac.aut.comp713.clinic.controller.PatientRequest;
import nz.ac.aut.comp713.clinic.controller.PatientResponse;
import nz.ac.aut.comp713.clinic.model.Appointment;
import nz.ac.aut.comp713.clinic.model.Patient;
import nz.ac.aut.comp713.clinic.repository.AppointmentRepository;
import nz.ac.aut.comp713.clinic.repository.PatientRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Patient use cases: {@code register}, {@code list}, {@code details}.
 *
 * <p>Course rules applied (week-5 lecture): the service performs the business use
 * case inside one transaction per operation, dependencies are constructor-injected
 * and the service is stateless (no request data in fields). The duplicate check runs
 * before any write; the {@code uk_patient_email} constraint settles the race if two
 * registrations pass the check at the same time, and the service translates that
 * constraint violation into the same clear conflict error.</p>
 */
@Service
public class PatientService {

    private final PatientRepository patients;
    private final AppointmentRepository appointments;

    public PatientService(PatientRepository patients, AppointmentRepository appointments) {
        this.patients = patients;
        this.appointments = appointments;
    }

    /** Registers a new patient and returns the stored representation. */
    @Transactional
    public PatientResponse register(PatientRequest request) {
        String email = normalise(request.email());
        if (patients.existsByEmailIgnoreCase(email)) {
            throw new PatientExistsException(email);
        }
        Patient saved;
        try {
            saved = patients.save(new Patient(
                    normalise(request.firstName()),
                    normalise(request.lastName()),
                    email,
                    emptyToNull(request.phone())));
        } catch (DataIntegrityViolationException ex) {
            // Final protection: uk_patient_email rejected a duplicate that slipped
            // past the check above because the two registrations raced.
            throw new PatientExistsException(email);
        }
        return patientResponse(saved);
    }

    /**
     * Lists patients, optionally filtered by last name (case-insensitive).
     */
    @Transactional(readOnly = true)
    public List<PatientResponse> list(String lastName) {
        List<Patient> found = (lastName == null || lastName.isBlank())
                ? patients.findAllByOrderByLastNameAscFirstNameAsc()
                : patients.findByLastNameIgnoreCase(normalise(lastName));
        return found.stream().map(this::patientResponse).toList();
    }

    /** One patient with their appointments in start-time order. */
    @Transactional(readOnly = true)
    public PatientDetailResponse details(long id) {
        Patient patient = patients.findById(id)
                .orElseThrow(() -> new PatientNotFoundException(id));
        List<AppointmentResponse> booked = appointments.findByPatientIdOrderByStartAtAsc(id)
                .stream().map(this::appointmentResponse).toList();
        return new PatientDetailResponse(patient.getId(), patient.getFirstName(), patient.getLastName(),
                patient.getEmail(), patient.getPhone(), List.copyOf(booked));
    }

    // Entity -> response mapping happens inside the service so controllers never
    // touch the persistence model (and lazy data is read inside the transaction).

    private PatientResponse patientResponse(Patient patient) {
        return new PatientResponse(patient.getId(), patient.getFirstName(), patient.getLastName(),
                patient.getEmail(), patient.getPhone());
    }

    private AppointmentResponse appointmentResponse(Appointment appointment) {
        return new AppointmentResponse(appointment.getId(), appointment.getPatientId(),
                appointment.getStartAt(), appointment.getEndAt(), appointment.getReason());
    }

    private static String normalise(String value) {
        return value == null ? null : value.trim();
    }

    private static String emptyToNull(String value) {
        String trimmed = normalise(value);
        return (trimmed == null || trimmed.isEmpty()) ? null : trimmed;
    }
}
