package nz.ac.aut.comp713.clinic.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Request body for booking or rescheduling an appointment. Bean validation rejects
 * a malformed shape (missing times, silly durations) at the API boundary; the
 * service layer then enforces the business time rules (future only, within opening
 * hours) and the no-overlap rule.
 */
public record AppointmentRequest(

        @NotNull(message = "must be provided")
        LocalDateTime startAt,

        @NotNull(message = "must be provided")
        @Min(value = 10, message = "must be at least 10 minutes")
        @Max(value = 180, message = "must be at most 180 minutes")
        Integer durationMinutes,

        @Size(max = 200, message = "must be at most 200 characters")
        String reason) {
}
