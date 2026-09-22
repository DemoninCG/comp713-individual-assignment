package nz.ac.aut.comp713.clinic.controller;

import java.time.LocalDateTime;

/**
 * Response body for one appointment (also embedded in {@link PatientDetailResponse}).
 */
public record AppointmentResponse(Long id, Long patientId, LocalDateTime startAt, LocalDateTime endAt,
                                  String reason) {
}
