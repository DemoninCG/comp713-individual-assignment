package nz.ac.aut.comp713.clinic;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Clinic Appointment System (COMP713 Assignment 2, Option A).
 *
 * <p>The application is organised in the course-standard layers:
 * {@code controller -> service -> repository -> database}. Controllers translate HTTP,
 * services perform business use cases (register a patient, book an appointment),
 * repositories access the shared data. Static HTML/JS client pages under
 * {@code src/main/resources/static} consume the JSON API.</p>
 */
@OpenAPIDefinition(info = @Info(
        title = "Clinic Appointment API",
        version = "1.0",
        description = "REST API for registering clinic patients and managing their appointments"))
@SpringBootApplication
public class ClinicApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicApplication.class, args);
    }
}
