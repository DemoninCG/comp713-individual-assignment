package nz.ac.aut.comp713.clinic.controller;

import java.util.List;

/**
 * Response body for the patient detail view: the patient plus their booked
 * appointments in start-time order (the relationship between the two entities).
 */
public record PatientDetailResponse(Long id, String firstName, String lastName, String email, String phone,
                                    List<AppointmentResponse> appointments) {
}
