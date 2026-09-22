package nz.ac.aut.comp713.clinic.controller;

/**
 * Response body for a patient. Controllers return records like this one, never the
 * JPA entity itself (the week-5 lab rule: the API surface is separate from the
 * persistence model).
 */
public record PatientResponse(Long id, String firstName, String lastName, String email, String phone) {
}
