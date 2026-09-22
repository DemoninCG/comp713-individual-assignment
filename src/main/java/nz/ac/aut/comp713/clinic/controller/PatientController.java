package nz.ac.aut.comp713.clinic.controller;

import nz.ac.aut.comp713.clinic.service.PatientService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;

/**
 * REST API for the patient registry. This controller only translates HTTP: it
 * validates the request shape, calls the service use cases, and maps results to
 * responses (week-5 lecture rule: controllers translate, services perform use
 * cases).
 *
 * <ul>
 *   <li>{@code POST /api/v1/patients} — register → 201 + {@code Location}</li>
 *   <li>{@code GET  /api/v1/patients?lastName=} — list (optional filter)</li>
 *   <li>{@code GET  /api/v1/patients/{id}} — detail incl. appointments</li>
 * </ul>
 */
@RestController
@RequestMapping(path = "/api/v1/patients", produces = MediaType.APPLICATION_JSON_VALUE)
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PatientResponse> register(@Valid @RequestBody PatientRequest request) {
        PatientResponse created = patientService.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<PatientResponse> list(@RequestParam(required = false) String lastName) {
        return patientService.list(lastName);
    }

    @GetMapping("/{id}")
    public PatientDetailResponse details(@PathVariable long id) {
        return patientService.details(id);
    }
}
