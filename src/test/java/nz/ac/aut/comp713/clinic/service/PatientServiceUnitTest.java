package nz.ac.aut.comp713.clinic.service;

import nz.ac.aut.comp713.clinic.controller.AppointmentResponse;
import nz.ac.aut.comp713.clinic.controller.PatientDetailResponse;
import nz.ac.aut.comp713.clinic.controller.PatientRequest;
import nz.ac.aut.comp713.clinic.controller.PatientResponse;
import nz.ac.aut.comp713.clinic.model.Appointment;
import nz.ac.aut.comp713.clinic.model.Patient;
import nz.ac.aut.comp713.clinic.repository.AppointmentRepository;
import nz.ac.aut.comp713.clinic.repository.PatientRepository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests: the service is constructed directly with mocked repositories
 * (the week-5 lab's unit-test style), so business-rule <em>ordering</em> can be
 * asserted — e.g. the duplicate check must reject a registration before any write.
 */
class PatientServiceUnitTest {

    private final PatientRepository patients = mock(PatientRepository.class);
    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final PatientService service = new PatientService(patients, appointments);

    private static PatientRequest request(String firstName, String lastName, String email, String phone) {
        return new PatientRequest(firstName, lastName, email, phone);
    }

    @Test
    void duplicateEmailIsRejectedBeforeAnyPatientIsSaved() {
        when(patients.existsByEmailIgnoreCase("aroha.ngata@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                request("Aroha", "Ngata", "aroha.ngata@example.com", null)))
                .isInstanceOf(PatientExistsException.class);

        verify(patients, never()).save(any(Patient.class));
    }

    @Test
    void registerTrimsInputAndBlanksThePhoneToNull() {
        when(patients.existsByEmailIgnoreCase("aroha.ngata@example.com")).thenReturn(false);
        when(patients.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatientResponse response = service.register(
                request("  Aroha ", " Ngata", " aroha.ngata@example.com ", "  "));

        ArgumentCaptor<Patient> saved = ArgumentCaptor.forClass(Patient.class);
        verify(patients).save(saved.capture());
        assertThat(saved.getValue().getFirstName()).isEqualTo("Aroha");
        assertThat(saved.getValue().getLastName()).isEqualTo("Ngata");
        assertThat(saved.getValue().getEmail()).isEqualTo("aroha.ngata@example.com");
        assertThat(saved.getValue().getPhone()).isNull();
        assertThat(response.email()).isEqualTo("aroha.ngata@example.com");
    }

    @Test
    void aUniqueConstraintRaceIsTranslatedToTheDuplicateError() {
        when(patients.existsByEmailIgnoreCase("aroha.ngata@example.com")).thenReturn(false);
        when(patients.save(any(Patient.class)))
                .thenThrow(new DataIntegrityViolationException("uk_patient_email"));

        assertThatThrownBy(() -> service.register(
                request("Aroha", "Ngata", "aroha.ngata@example.com", null)))
                .isInstanceOf(PatientExistsException.class);
    }

    @Test
    void unknownPatientFailsFastWithoutLoadingAppointments() {
        when(patients.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.details(99))
                .isInstanceOf(PatientNotFoundException.class);

        verifyNoInteractions(appointments);
    }

    @Test
    void detailsMapsThePatientWithTheirAppointments() {
        Patient patient = new Patient("Aroha", "Ngata", "aroha.ngata@example.com", "021 555 0101");
        when(patients.findById(1L)).thenReturn(Optional.of(patient));
        Patient attached = patient;
        Appointment appointment = new Appointment(attached,
                LocalDateTime.of(2030, 1, 7, 10, 0), LocalDateTime.of(2030, 1, 7, 11, 0), "Check-up");
        when(appointments.findByPatientIdOrderByStartAtAsc(1L)).thenReturn(List.of(appointment));

        PatientDetailResponse detail = service.details(1L);

        assertThat(detail.firstName()).isEqualTo("Aroha");
        assertThat(detail.appointments()).hasSize(1);
        AppointmentResponse booked = detail.appointments().getFirst();
        assertThat(booked.reason()).isEqualTo("Check-up");
        assertThat(booked.startAt()).isEqualTo(LocalDateTime.of(2030, 1, 7, 10, 0));
    }

    @Test
    void listWithoutAFilterReturnsEveryPatient() {
        when(patients.findAllByOrderByLastNameAscFirstNameAsc())
                .thenReturn(List.of(new Patient("Aroha", "Ngata", "a@example.com", null)));

        List<PatientResponse> listed = service.list(null);

        assertThat(listed).hasSize(1);
        verify(patients, never()).findByLastNameIgnoreCase(any());
    }

    @Test
    void listWithAFilterUsesTheCaseInsensitiveLookup() {
        when(patients.findByLastNameIgnoreCase("Ngata"))
                .thenReturn(List.of(new Patient("Aroha", "Ngata", "a@example.com", null)));

        List<PatientResponse> listed = service.list("  Ngata ");

        assertThat(listed).hasSize(1);
        verify(patients, never()).findAllByOrderByLastNameAscFirstNameAsc();
    }
}
