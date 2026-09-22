package nz.ac.aut.comp713.clinic.service;

/**
 * Thrown when an appointment id does not exist. Mapped to HTTP 404 by the API layer.
 */
public class AppointmentNotFoundException extends RuntimeException {

    public AppointmentNotFoundException(long id) {
        super("Appointment " + id + " was not found");
    }
}
