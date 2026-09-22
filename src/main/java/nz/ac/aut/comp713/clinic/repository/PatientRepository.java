package nz.ac.aut.comp713.clinic.repository;

import jakarta.persistence.LockModeType;
import nz.ac.aut.comp713.clinic.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Reads the patient with a pessimistic row lock ({@code SELECT ... FOR UPDATE})
     * that is held until the transaction commits. Booking changes for one patient
     * take this lock first so their overlap check and write cannot interleave with
     * another booking for the same patient — the week-5 lecture's pessimistic
     * row-locking control, needed because H2 has no range-exclusion constraint.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Patient p where p.id = :id")
    Optional<Patient> findByIdForUpdate(@Param("id") long id);
}
