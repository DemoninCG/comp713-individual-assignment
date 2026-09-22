package nz.ac.aut.comp713.clinic.service;

import nz.ac.aut.comp713.clinic.controller.AppointmentRequest;
import nz.ac.aut.comp713.clinic.controller.AppointmentResponse;
import nz.ac.aut.comp713.clinic.model.Appointment;
import nz.ac.aut.comp713.clinic.model.Patient;
import nz.ac.aut.comp713.clinic.repository.AppointmentRepository;
import nz.ac.aut.comp713.clinic.repository.PatientRepository;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (the week-5 lab's unit-test style): the service is constructed
 * directly with mocked repositories so the business rules, their ordering, and the
 * three concurrency-defence layers can be asserted in isolation.
 */
class AppointmentServiceUnitTest {

    private static final long PATIENT_ID = 1L;
    private static final long APPOINTMENT_ID = 5L;
    /** A fixed Monday morning slot, safely in the future. */
    private static final LocalDateTime SLOT = LocalDateTime.of(2030, 1, 7, 10, 0);

    private final PatientRepository patients = mock(PatientRepository.class);
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final AppointmentService service = new AppointmentService(patients, appointments);

    private static AppointmentRequest request(LocalDateTime startAt, int durationMinutes) {
        return new AppointmentRequest(startAt, durationMinutes, "Check-up");
    }

    private Patient aKnownPatient() {
        Patient patient = new Patient("Aroha", "Ngata", "aroha.ngata@example.com", null);
        when(patients.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        return patient;
    }

    private Appointment existingAppointment(long id, LocalDateTime startAt, LocalDateTime endAt) {
        Appointment appointment = mock(Appointment.class);
        when(appointment.getId()).thenReturn(id);
        when(appointment.getPatientId()).thenReturn(PATIENT_ID);
        when(appointment.getStartAt()).thenReturn(startAt);
        when(appointment.getEndAt()).thenReturn(endAt);
        return appointment;
    }

    // --- layer 1: fail-fast checks happen before any write ---

    @Test
    void unknownPatientIsRejectedBeforeAnyAppointmentWrite() {
        when(patients.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bookAppointment(99L, request(SLOT, 30)))
                .isInstanceOf(PatientNotFoundException.class);

        verifyNoInteractions(appointments);
    }

    @Test
    void pastStartIsRejectedBeforeAnyWrite() {
        aKnownPatient();

        assertThatThrownBy(() -> service.bookAppointment(PATIENT_ID,
                request(LocalDateTime.of(2020, 1, 6, 10, 0), 30)))
                .isInstanceOf(InvalidAppointmentTimeException.class)
                .hasMessageContaining("future");

        verifyNoInteractions(appointments);
    }

    @Test
    void weekendBookingsAreRejected() {
        aKnownPatient();

        assertThatThrownBy(() -> service.bookAppointment(PATIENT_ID,
                request(LocalDateTime.of(2030, 1, 6, 10, 0), 30))) // a Sunday
                .isInstanceOf(InvalidAppointmentTimeException.class)
                .hasMessageContaining("Monday to Friday");
    }

    @Test
    void bookingsOutsideOpeningHoursAreRejected() {
        aKnownPatient();

        assertThatThrownBy(() -> service.bookAppointment(PATIENT_ID, request(LocalDateTime.of(2030, 1, 7, 8, 0), 30)))
                .isInstanceOf(InvalidAppointmentTimeException.class);
        assertThatThrownBy(() -> service.bookAppointment(PATIENT_ID, request(LocalDateTime.of(2030, 1, 7, 16, 45), 60)))
                .isInstanceOf(InvalidAppointmentTimeException.class)
                .hasMessageContaining("opening hours");
    }

    @Test
    void overlappingBookingIsRejectedBeforeTheAtomicWrite() {
        aKnownPatient();
        Appointment existing = existingAppointment(9L, SLOT, SLOT.plusMinutes(60));
        when(appointments.findByPatientIdOrderByStartAtAsc(PATIENT_ID)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.bookAppointment(PATIENT_ID, request(SLOT.plusMinutes(30), 30)))
                .isInstanceOf(AppointmentConflictException.class);

        verify(patients).findByIdForUpdate(PATIENT_ID); // per-patient serialisation lock
        verify(appointments, never()).insertIfNoOverlap(any(), any(), any(), any(), any());
    }

    // --- layer 2: the atomic check-and-write decides close races ---

    @Test
    void aFreeSlotIsBookedWithTheAtomicInsert() {
        aKnownPatient();
        when(appointments.findByPatientIdOrderByStartAtAsc(PATIENT_ID)).thenReturn(List.of());
        when(appointments.insertIfNoOverlap(eq(PATIENT_ID), eq(SLOT), eq(SLOT.plusMinutes(30)),
                eq("Check-up"), any()))
                .thenReturn(1);
        when(appointments.findByPatientIdAndStartAt(PATIENT_ID, SLOT))
                .thenReturn(Optional.of(new Appointment(new Patient("Aroha", "Ngata", "a@example.com", null),
                        SLOT, SLOT.plusMinutes(30), "Check-up")));

        AppointmentResponse booked = service.bookAppointment(PATIENT_ID, request(SLOT, 30));

        assertThat(booked.patientId()).isEqualTo(PATIENT_ID);
        assertThat(booked.startAt()).isEqualTo(SLOT);
        assertThat(booked.reason()).isEqualTo("Check-up");
    }

    @Test
    void backToBackBookingsPassTheOverlapCheck() {
        aKnownPatient();
        Appointment existing = existingAppointment(9L, SLOT, SLOT.plusMinutes(60));
        when(appointments.findByPatientIdOrderByStartAtAsc(PATIENT_ID)).thenReturn(List.of(existing));
        when(appointments.insertIfNoOverlap(any(), any(), any(), any(), any())).thenReturn(1);
        when(appointments.findByPatientIdAndStartAt(PATIENT_ID, SLOT.plusMinutes(60)))
                .thenReturn(Optional.of(new Appointment(new Patient("Aroha", "Ngata", "a@example.com", null),
                        SLOT.plusMinutes(60), SLOT.plusMinutes(90), "Check-up")));

        AppointmentResponse booked = service.bookAppointment(PATIENT_ID, request(SLOT.plusMinutes(60), 30));

        assertThat(booked.startAt()).isEqualTo(SLOT.plusMinutes(60));
    }

    @Test
    void losingTheAtomicInsertRaceIsReportedAsAConflict() {
        aKnownPatient();
        when(appointments.findByPatientIdOrderByStartAtAsc(PATIENT_ID)).thenReturn(List.of());
        when(appointments.insertIfNoOverlap(any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.bookAppointment(PATIENT_ID, request(SLOT, 30)))
                .isInstanceOf(AppointmentConflictException.class);
    }

    @Test
    void theUniqueConstraintBackstopIsTranslatedToAConflict() {
        aKnownPatient();
        when(appointments.findByPatientIdOrderByStartAtAsc(PATIENT_ID)).thenReturn(List.of());
        when(appointments.insertIfNoOverlap(any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("uk_appointment_patient_start"));

        assertThatThrownBy(() -> service.bookAppointment(PATIENT_ID, request(SLOT, 30)))
                .isInstanceOf(AppointmentConflictException.class);
    }

    // --- rescheduling and cancelling ---

    @Test
    void rescheduleRejectsMovingOntoAnotherBooking() {
        Appointment self = existingAppointment(APPOINTMENT_ID, SLOT, SLOT.plusMinutes(60));
        Appointment other = existingAppointment(6L, SLOT.plusHours(2), SLOT.plusHours(3));
        when(appointments.findById(APPOINTMENT_ID)).thenReturn(Optional.of(self));
        when(appointments.findByPatientIdOrderByStartAtAsc(PATIENT_ID)).thenReturn(List.of(self, other));

        assertThatThrownBy(() -> service.rescheduleAppointment(APPOINTMENT_ID,
                request(SLOT.plusHours(2).plusMinutes(30), 60)))
                .isInstanceOf(AppointmentConflictException.class);

        verify(appointments, never()).updateIfNoOverlap(any(), any(), any(), any(), any());
    }

    @Test
    void rescheduleExcludesItsOwnSlotFromTheOverlapCheck() {
        Appointment self = existingAppointment(APPOINTMENT_ID, SLOT, SLOT.plusMinutes(60));
        when(appointments.findById(APPOINTMENT_ID)).thenReturn(Optional.of(self));
        when(appointments.findByPatientIdOrderByStartAtAsc(PATIENT_ID)).thenReturn(List.of(self));
        when(appointments.updateIfNoOverlap(APPOINTMENT_ID, PATIENT_ID, SLOT, SLOT.plusMinutes(60), "Check-up"))
                .thenReturn(1);

        AppointmentResponse moved = service.rescheduleAppointment(APPOINTMENT_ID, request(SLOT, 60));

        verify(appointments).updateIfNoOverlap(APPOINTMENT_ID, PATIENT_ID, SLOT, SLOT.plusMinutes(60), "Check-up");
        assertThat(moved.id()).isEqualTo(APPOINTMENT_ID);
        assertThat(moved.endAt()).isEqualTo(SLOT.plusMinutes(60));
    }

    @Test
    void losingTheAtomicUpdateRaceIsReportedAsAConflict() {
        Appointment self = existingAppointment(APPOINTMENT_ID, SLOT, SLOT.plusMinutes(60));
        when(appointments.findById(APPOINTMENT_ID)).thenReturn(Optional.of(self));
        when(appointments.findByPatientIdOrderByStartAtAsc(PATIENT_ID)).thenReturn(List.of());
        when(appointments.updateIfNoOverlap(any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.rescheduleAppointment(APPOINTMENT_ID,
                request(SLOT.plusHours(3), 30)))
                .isInstanceOf(AppointmentConflictException.class);
    }

    @Test
    void unknownAppointmentIsRejectedWhenReadRescheduledOrCancelled() {
        when(appointments.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAppointment(99L)).isInstanceOf(AppointmentNotFoundException.class);
        assertThatThrownBy(() -> service.rescheduleAppointment(99L, request(SLOT, 30)))
                .isInstanceOf(AppointmentNotFoundException.class);
        assertThatThrownBy(() -> service.cancelAppointment(99L)).isInstanceOf(AppointmentNotFoundException.class);

        verify(appointments, never()).delete(any());
        verify(appointments, never()).updateIfNoOverlap(any(), any(), any(), any(), any());
    }

    @Test
    void cancelDeletesTheAppointment() {
        Appointment appointment = existingAppointment(APPOINTMENT_ID, SLOT, SLOT.plusMinutes(60));
        when(appointments.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        service.cancelAppointment(APPOINTMENT_ID);

        verify(appointments).delete(appointment);
    }
}
