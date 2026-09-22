package nz.ac.aut.comp713.clinic.controller;

import nz.ac.aut.comp713.clinic.service.PatientExistsException;
import nz.ac.aut.comp713.clinic.service.PatientNotFoundException;
import nz.ac.aut.comp713.clinic.service.PatientService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller contract tests (the week-6 lab's @WebMvcTest style): status codes,
 * the 201 + Location create semantics, and the ApiError shape for every failure
 * case. The service is mocked so only the HTTP contract is under test.
 */
@WebMvcTest(PatientController.class)
@Import(GlobalExceptionHandler.class)
class PatientControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PatientService patientService;

    private static PatientResponse response(long id) {
        return new PatientResponse(id, "Aroha", "Ngata", "aroha.ngata@example.com", "021 555 0101");
    }

    @Test
    void registerReturns201WithLocationAndBody() throws Exception {
        when(patientService.register(any())).thenReturn(response(42L));

        mvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Aroha","lastName":"Ngata",
                                 "email":"aroha.ngata@example.com","phone":"021 555 0101"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/patients/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.email").value("aroha.ngata@example.com"));
    }

    @Test
    void invalidBodyUsesTheApiErrorModel() throws Exception {
        mvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"","lastName":"Ngata","email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/v1/patients"))
                .andExpect(jsonPath("$.message", containsString("firstName")))
                .andExpect(jsonPath("$.message", containsString("email")));
    }

    @Test
    void malformedJsonIsA400WithApiError() throws Exception {
        mvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/v1/patients"));
    }

    @Test
    void duplicateRegistrationIsA409WithApiError() throws Exception {
        when(patientService.register(any()))
                .thenThrow(new PatientExistsException("aroha.ngata@example.com"));

        mvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Aroha","lastName":"Ngata","email":"aroha.ngata@example.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_EXISTS"))
                .andExpect(jsonPath("$.message", containsString("aroha.ngata@example.com")));
    }

    @Test
    void unknownPatientIsA404WithApiError() throws Exception {
        when(patientService.details(99L)).thenThrow(new PatientNotFoundException(99L));

        mvc.perform(get("/api/v1/patients/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/patients/99"));
    }

    @Test
    void nonNumericPatientIdIsA400WithApiError() throws Exception {
        mvc.perform(get("/api/v1/patients/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/v1/patients/abc"));
    }

    @Test
    void listPassesTheLastNameFilterToTheService() throws Exception {
        when(patientService.list("Ngata")).thenReturn(List.of(response(1L)));

        mvc.perform(get("/api/v1/patients").param("lastName", "Ngata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastName").value("Ngata"));
    }

    @Test
    void detailEmbedsThePatientsAppointments() throws Exception {
        when(patientService.details(1L)).thenReturn(new PatientDetailResponse(
                1L, "Aroha", "Ngata", "aroha.ngata@example.com", null,
                List.of(new AppointmentResponse(5L, 1L,
                        java.time.LocalDateTime.of(2030, 1, 7, 10, 0),
                        java.time.LocalDateTime.of(2030, 1, 7, 11, 0), "Check-up"))));

        mvc.perform(get("/api/v1/patients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointments[0].reason").value("Check-up"));
    }
}
