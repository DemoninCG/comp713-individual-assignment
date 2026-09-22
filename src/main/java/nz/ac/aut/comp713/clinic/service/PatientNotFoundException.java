package nz.ac.aut.comp713.clinic.service;

/**
 * Thrown when a patient id does not exist. Mapped to HTTP 404 by the API layer.
 */
public class PatientNotFoundException extends RuntimeException {

    public PatientNotFoundException(long id) {
        super("Patient " + id + " was not found");
    }
}
