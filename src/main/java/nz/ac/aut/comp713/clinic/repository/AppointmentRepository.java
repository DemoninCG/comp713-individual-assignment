package nz.ac.aut.comp713.clinic.repository;

import nz.ac.aut.comp713.clinic.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persistence operations for {@link Appointment}.
 *
 * <p>Reads and deletes are derived queries; the concurrency-sensitive writes live
 * in {@link AppointmentSlotWrites} so that the check for an overlapping slot and
 * the write happen in one atomic database statement.</p>
 */
public interface AppointmentRepository extends JpaRepository<Appointment, Long>, AppointmentSlotWrites {

    List<Appointment> findByPatientIdOrderByStartAtAsc(Long patientId);

    Optional<Appointment> findByPatientIdAndStartAt(Long patientId, LocalDateTime startAt);
}
