package nz.ac.aut.comp713.clinic.service;

/**
 * Thrown when a requested appointment time breaks a scheduling rule (not in the
 * future, at a weekend, or not inside the clinic's opening hours). Mapped to HTTP
 * 400 with the stable error code {@code APPOINTMENT_TIME_INVALID}.
 */
public class InvalidAppointmentTimeException extends RuntimeException {

    public InvalidAppointmentTimeException(String message) {
        super(message);
    }
}
