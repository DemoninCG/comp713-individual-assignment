package nz.ac.aut.comp713.clinic.controller;

import nz.ac.aut.comp713.clinic.service.AppointmentService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.validation.Valid;

import java.net.URI;

/**
 * REST API for the appointment workflow. This controller only translates HTTP; the
 * scheduling rules and concurrency handling live in {@link AppointmentService}.
 *
 * <ul>
 *   <li>{@code POST   /api/v1/patients/{patientId}/appointments} — book → 201 + {@code Location}</li>
 *   <li>{@code GET    /api/v1/appointments/{id}} — read a booked slot</li>
 *   <li>{@code PUT    /api/v1/appointments/{id}} — reschedule → 200</li>
 *   <li>{@code DELETE /api/v1/appointments/{id}} — cancel → 204</li>
 * </ul>
 */
@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping(path = "/patients/{patientId}/appointments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AppointmentResponse> book(@PathVariable long patientId,
                                                    @Valid @RequestBody AppointmentRequest request) {
        AppointmentResponse created = appointmentService.bookAppointment(patientId, request);
        // Location points at the canonical appointment resource (also the GET/PUT/DELETE target).
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/v1/appointments/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/appointments/{id}")
    public AppointmentResponse get(@PathVariable long id) {
        return appointmentService.getAppointment(id);
    }

    @PutMapping(path = "/appointments/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AppointmentResponse reschedule(@PathVariable long id,
                                          @Valid @RequestBody AppointmentRequest request) {
        return appointmentService.rescheduleAppointment(id, request);
    }

    @DeleteMapping("/appointments/{id}")
    public ResponseEntity<Void> cancel(@PathVariable long id) {
        appointmentService.cancelAppointment(id);
        return ResponseEntity.noContent().build();
    }
}
