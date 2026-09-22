package nz.ac.aut.comp713.clinic.service;

import java.time.LocalDateTime;

/**
 * Thrown when a booking overlaps an existing appointment of the same patient.
 * Mapped to HTTP 409 by the API layer. Raised by the fail-fast overlap check and
 * when the atomic check-and-write statement or the {@code
 * uk_appointment_patient_start} constraint rejects a racing request.
 */
public class AppointmentConflictException extends RuntimeException {

    public AppointmentConflictException(long patientId, LocalDateTime startAt) {
        super("Patient " + patientId + " already has an appointment overlapping " + startAt);
    }
}
