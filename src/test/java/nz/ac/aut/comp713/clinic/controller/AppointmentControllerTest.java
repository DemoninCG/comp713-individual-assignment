package nz.ac.aut.comp713.clinic.controller;

import nz.ac.aut.comp713.clinic.service.AppointmentConflictException;
import nz.ac.aut.comp713.clinic.service.AppointmentNotFoundException;
import nz.ac.aut.comp713.clinic.service.AppointmentService;
import nz.ac.aut.comp713.clinic.service.InvalidAppointmentTimeException;
import nz.ac.aut.comp713.clinic.service.PatientNotFoundException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller contract tests for the appointment workflow (week-6 lab @WebMvcTest
 * style): 201 + Location on booking, 200/204 on update/delete, and the ApiError
 * shape for every failure case.
 */
@WebMvcTest(AppointmentController.class)
@Import(GlobalExceptionHandler.class)
class AppointmentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AppointmentService appointmentService;

    private static final String BODY = """
            {"startAt":"2030-01-07T10:00:00","durationMinutes":30,"reason":"Check-up"}
            """;

    private static AppointmentResponse slot(long id) {
        return new AppointmentResponse(id, 1L,
                LocalDateTime.of(2030, 1, 7, 10, 0), LocalDateTime.of(2030, 1, 7, 10, 30), "Check-up");
    }

    @Test
    void bookReturns201WithLocationPointingAtTheAppointmentResource() throws Exception {
        when(appointmentService.bookAppointment(eq(1L), any())).thenReturn(slot(5L));

        mvc.perform(post("/api/v1/patients/1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/appointments/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.reason").value("Check-up"));
    }

    @Test
    void bookingWithoutADurationIsA400WithApiError() throws Exception {
        mvc.perform(post("/api/v1/patients/1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startAt":"2030-01-07T10:00:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("durationMinutes")));
    }

    @Test
    void invalidTimeIsA400AppointmentTimeInvalid() throws Exception {
        when(appointmentService.bookAppointment(eq(1L), any()))
                .thenThrow(new InvalidAppointmentTimeException("The appointment must start in the future"));

        mvc.perform(post("/api/v1/patients/1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_TIME_INVALID"))
                .andExpect(jsonPath("$.message", containsString("future")));
    }

    @Test
    void overlappingBookingIsA409AppointmentConflict() throws Exception {
        when(appointmentService.bookAppointment(eq(1L), any()))
                .thenThrow(new AppointmentConflictException(1L, LocalDateTime.of(2030, 1, 7, 10, 0)));

        mvc.perform(post("/api/v1/patients/1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_CONFLICT"))
                .andExpect(jsonPath("$.path").value("/api/v1/patients/1/appointments"));
    }

    @Test
    void unknownPatientIsA404WithApiError() throws Exception {
        when(appointmentService.bookAppointment(eq(99L), any()))
                .thenThrow(new PatientNotFoundException(99L));

        mvc.perform(post("/api/v1/patients/99/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));
    }

    @Test
    void unknownAppointmentIsA404WithApiError() throws Exception {
        when(appointmentService.getAppointment(99L)).thenThrow(new AppointmentNotFoundException(99L));

        mvc.perform(get("/api/v1/appointments/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/appointments/99"));
    }

    @Test
    void getReturnsTheBookedSlot() throws Exception {
        when(appointmentService.getAppointment(5L)).thenReturn(slot(5L));

        mvc.perform(get("/api/v1/appointments/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.startAt").value("2030-01-07T10:00:00"));
    }

    @Test
    void rescheduleReturns200WithTheUpdatedSlot() throws Exception {
        when(appointmentService.rescheduleAppointment(eq(5L), any())).thenReturn(slot(5L));

        mvc.perform(put("/api/v1/appointments/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.startAt").value("2030-01-07T10:00:00"));
    }

    @Test
    void cancelReturns204NoContent() throws Exception {
        mvc.perform(delete("/api/v1/appointments/5"))
                .andExpect(status().isNoContent());
    }
}
