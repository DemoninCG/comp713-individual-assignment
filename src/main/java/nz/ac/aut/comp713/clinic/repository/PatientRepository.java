package nz.ac.aut.comp713.clinic.repository;

import nz.ac.aut.comp713.clinic.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence operations for {@link Patient}.
 *
 * <p>Derived queries only: business rules (duplicate e-mail handling, validation)
 * belong to the service layer, and the {@code uk_patient_email} unique constraint
 * protects the duplicate rule when requests race.</p>
 */
public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<Patient> findByLastNameIgnoreCase(String lastName);

    List<Patient> findAllByOrderByLastNameAscFirstNameAsc();
}
