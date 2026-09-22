package nz.ac.aut.comp713.clinic.service;

/**
 * Thrown when a registration uses an e-mail address that is already registered.
 * Mapped to HTTP 409 by the API layer. Raised both by the fail-fast duplicate check
 * and when the {@code uk_patient_email} constraint rejects a racing registration.
 */
public class PatientExistsException extends RuntimeException {

    public PatientExistsException(String email) {
        super("A patient with e-mail " + email + " is already registered");
    }
}
