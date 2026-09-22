package nz.ac.aut.comp713.clinic.repository;

import nz.ac.aut.comp713.clinic.model.Appointment;
import nz.ac.aut.comp713.clinic.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence checks for appointments: the relationship to patients, the named
 * database constraints, and the atomic check-and-write slot statements.
 */
@SpringBootTest
class AppointmentRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2030, 1, 7); // a fixed Monday

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private PatientRepository patients;

    private Patient patient;

    @BeforeEach
    void resetDatabase() {
        appointments.deleteAll();
        patients.deleteAll();
        patient = patients.save(new Patient("Aroha", "Ngata", "aroha.ngata@example.com", "021 555 0101"));
    }

    private static LocalDateTime at(int hour, int minute) {
        return DAY.atTime(hour, minute);
    }

    // --- atomic conditional insert (booking) ---

    @Test
    void insertIfNoOverlapWritesExactlyOneRow() {
        int rows = appointments.insertIfNoOverlap(patient.getId(), at(10, 0), at(11, 0), "Check-up", LocalDateTime.now());

        assertThat(rows).isEqualTo(1);
        Appointment saved = appointments.findByPatientIdAndStartAt(patient.getId(), at(10, 0)).orElseThrow();
        assertThat(saved.getReason()).isEqualTo("Check-up");
        assertThat(saved.getEndAt()).isEqualTo(at(11, 0));
    }

    @Test
    void insertIfNoOverlapRejectsAnOverlappingSlot() {
        appointments.insertIfNoOverlap(patient.getId(), at(10, 0), at(11, 0), "Check-up", LocalDateTime.now());

        int rows = appointments.insertIfNoOverlap(patient.getId(), at(10, 30), at(11, 30), "Overlaps", LocalDateTime.now());

        assertThat(rows).isZero();
        assertThat(appointments.findByPatientIdOrderByStartAtAsc(patient.getId())).hasSize(1);
    }

    @Test
    void insertIfNoOverlapAllowsABackToBackSlot() {
        assertThat(appointments.insertIfNoOverlap(patient.getId(), at(10, 0), at(11, 0), "First", LocalDateTime.now())).isEqualTo(1);
        assertThat(appointments.insertIfNoOverlap(patient.getId(), at(11, 0), at(11, 30), "Second", LocalDateTime.now())).isEqualTo(1);
    }

    @Test
    void insertIfNoOverlapAllowsABookingWithoutAReason() {
        assertThat(appointments.insertIfNoOverlap(patient.getId(), at(10, 0), at(11, 0), null, LocalDateTime.now())).isEqualTo(1);
    }

    // --- atomic conditional update (rescheduling) ---

    @Test
    void updateIfNoOverlapMovesTheBooking() {
        appointments.insertIfNoOverlap(patient.getId(), at(10, 0), at(11, 0), "Check-up", LocalDateTime.now());
        Long id = appointments.findByPatientIdAndStartAt(patient.getId(), at(10, 0)).orElseThrow().getId();

        int rows = appointments.updateIfNoOverlap(id, patient.getId(), at(12, 0), at(13, 0), "Check-up");

        assertThat(rows).isEqualTo(1);
        Appointment moved = appointments.findById(id).orElseThrow();
        assertThat(moved.getStartAt()).isEqualTo(at(12, 0));
        assertThat(moved.getEndAt()).isEqualTo(at(13, 0));
    }

    @Test
    void updateIfNoOverlapRejectsMovingOntoAnotherBooking() {
        appointments.insertIfNoOverlap(patient.getId(), at(10, 0), at(11, 0), "First", LocalDateTime.now());
        appointments.insertIfNoOverlap(patient.getId(), at(12, 0), at(13, 0), "Second", LocalDateTime.now());
        Long firstId = appointments.findByPatientIdAndStartAt(patient.getId(), at(10, 0)).orElseThrow().getId();

        int rows = appointments.updateIfNoOverlap(firstId, patient.getId(), at(12, 30), at(13, 30), "Moved");

        assertThat(rows).isZero();
        Appointment unchanged = appointments.findById(firstId).orElseThrow();
        assertThat(unchanged.getStartAt()).isEqualTo(at(10, 0));
        assertThat(unchanged.getReason()).isEqualTo("First");
    }

    @Test
    void updateIfNoOverlapExcludesTheBookingItselfFromTheOverlapCheck() {
        appointments.insertIfNoOverlap(patient.getId(), at(10, 0), at(11, 0), "Check-up", LocalDateTime.now());
        Long id = appointments.findByPatientIdAndStartAt(patient.getId(), at(10, 0)).orElseThrow().getId();

        int rows = appointments.updateIfNoOverlap(id, patient.getId(), at(10, 0), at(11, 0), "Rescheduled in place");

        assertThat(rows).isEqualTo(1);
        assertThat(appointments.findById(id).orElseThrow().getReason()).isEqualTo("Rescheduled in place");
    }

    // --- named database constraints (final protection) ---

    @Test
    void samePatientAndStartIsRejectedByTheUniqueConstraint() {
        appointments.saveAndFlush(new Appointment(patient, at(10, 0), at(11, 0), "First"));

        assertThatThrownBy(() -> appointments.saveAndFlush(new Appointment(patient, at(10, 0), at(10, 30), "Double-booked")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void endBeforeStartIsRejectedByTheCheckConstraint() {
        assertThatThrownBy(() -> appointments.saveAndFlush(new Appointment(patient, at(11, 0), at(10, 0), "Backwards")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- relationship behaviour ---

    @Test
    void deletingAPatientAlsoRemovesTheirAppointments() {
        appointments.insertIfNoOverlap(patient.getId(), at(10, 0), at(11, 0), "Check-up", LocalDateTime.now());

        patients.deleteById(patient.getId());

        assertThat(appointments.findByPatientIdOrderByStartAtAsc(patient.getId())).isEmpty();
    }

    @Test
    void appointmentsOfOnePatientAreListedInStartOrder() {
        appointments.insertIfNoOverlap(patient.getId(), at(14, 0), at(14, 30), "Later", LocalDateTime.now());
        appointments.insertIfNoOverlap(patient.getId(), at(9, 0), at(9, 30), "Earlier", LocalDateTime.now());

        List<Appointment> ordered = appointments.findByPatientIdOrderByStartAtAsc(patient.getId());

        assertThat(ordered).extracting(Appointment::getReason).containsExactly("Earlier", "Later");
    }
}
