package nz.ac.aut.comp713.clinic.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for registering a patient. Bean validation rejects invalid input at
 * the API boundary with HTTP 400 before the request reaches the service layer.
 */
public record PatientRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 50, message = "must be at most 50 characters")
        String firstName,

        @NotBlank(message = "must not be blank")
        @Size(max = 50, message = "must be at most 50 characters")
        String lastName,

        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid e-mail address")
        @Size(max = 100, message = "must be at most 100 characters")
        String email,

        @Size(max = 20, message = "must be at most 20 characters")
        @Pattern(regexp = "[0-9 +()\\-]*", message = "may contain only digits, spaces and + ( ) -")
        String phone) {
}
