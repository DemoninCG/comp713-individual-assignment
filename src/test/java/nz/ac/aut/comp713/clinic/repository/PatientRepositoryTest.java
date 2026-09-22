package nz.ac.aut.comp713.clinic.repository;

import nz.ac.aut.comp713.clinic.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence checks for patients: derived queries plus the database rules that
 * protect the data when requests race (week-5 lab convention: the database
 * constraint is the final protection). Tests reset the database explicitly so they
 * exercise real transactional behaviour.
 */
@SpringBootTest
class PatientRepositoryTest {

    @Autowired
    private PatientRepository patients;

    @Autowired
    private AppointmentRepository appointments;

    @BeforeEach
    void resetDatabase() {
        appointments.deleteAll();
        patients.deleteAll();
    }

    @Test
    void savedPatientIsFoundByEmailIgnoringCase() {
        patients.save(new Patient("Aroha", "Ngata", "aroha.ngata@example.com", "021 555 0101"));

        assertThat(patients.findByEmailIgnoreCase("AROHA.NGATA@EXAMPLE.COM"))
                .isPresent()
                .get().extracting(Patient::getLastName).isEqualTo("Ngata");
    }

    @Test
    void lastNameFilterMatchesIgnoringCase() {
        patients.save(new Patient("Aroha", "Ngata", "aroha.ngata@example.com", null));
        patients.save(new Patient("Sam", "Whitcombe", "sam.whitcombe@example.com", null));

        List<Patient> found = patients.findByLastNameIgnoreCase("ngata");

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getFirstName()).isEqualTo("Aroha");
    }

    @Test
    void duplicateEmailIsRejectedByTheUniqueConstraint() {
        patients.save(new Patient("Aroha", "Ngata", "aroha.ngata@example.com", null));

        assertThatThrownBy(() -> patients.saveAndFlush(new Patient("Aroha", "Rangi", "aroha.ngata@example.com", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
