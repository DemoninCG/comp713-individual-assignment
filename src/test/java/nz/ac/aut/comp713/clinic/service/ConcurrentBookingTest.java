package nz.ac.aut.comp713.clinic.service;

import nz.ac.aut.comp713.clinic.controller.AppointmentRequest;
import nz.ac.aut.comp713.clinic.model.Patient;
import nz.ac.aut.comp713.clinic.repository.AppointmentRepository;
import nz.ac.aut.comp713.clinic.repository.PatientRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency behaviour (the week-5 lab's concurrent-enrolment test pattern):
 * real threads book overlapping slots at the same instant. Because booking changes
 * for one patient serialise on the patient row lock and then run through the atomic
 * check-and-write statement, exactly one attempt may win and the database must end
 * up with exactly one row — whatever the thread interleaving.
 */
@SpringBootTest
class ConcurrentBookingTest {

    /** A fixed Monday morning slot, safely in the future. */
    private static final LocalDateTime SLOT = LocalDateTime.of(2030, 1, 7, 10, 0);

    @Autowired
    private PatientRepository patients;

    @Autowired
    private AppointmentRepository appointments;

    @Autowired
    private AppointmentService appointmentService;

    private Patient patient;

    @BeforeEach
    void resetDatabase() {
        appointments.deleteAll();
        patients.deleteAll();
        patient = patients.save(new Patient("Aroha", "Ngata", "aroha.ngata@example.com", null));
    }

    @Test
    void onlyOneOfTwoRacingBookingsForTheSamePatientWins() throws Exception {
        List<String> outcomes = race(
                () -> attempt(patient, SLOT, 60),                     // 10:00 - 11:00
                () -> attempt(patient, SLOT.plusMinutes(30), 60));    // 10:30 - 11:30 (overlaps)

        assertThat(outcomes).containsExactlyInAnyOrder("BOOKED", "CONFLICT");
        assertThat(appointments.count()).isEqualTo(1);
    }

    @Test
    void differentPatientsMayBookTheSameSlot() throws Exception {
        Patient other = patients.save(new Patient("Sam", "Whitcombe", "sam.whitcombe@example.com", null));

        // The no-overlap rule (and its lock) is per patient, so this must be 2 wins.
        List<String> outcomes = race(
                () -> attempt(patient, SLOT, 30),
                () -> attempt(other, SLOT, 30));

        assertThat(outcomes).containsExactlyInAnyOrder("BOOKED", "BOOKED");
        assertThat(appointments.count()).isEqualTo(2);
    }

    // --- helpers ---

    /**
     * Starts both booking attempts at the same instant (the latch is the "go"
     * signal) and collects their outcomes: "BOOKED" or "CONFLICT".
     */
    private List<String> race(Booking first, Booking second) throws Exception {
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> one = executor.submit(() -> {
                startTogether.await();
                return first.run();
            });
            Future<String> two = executor.submit(() -> {
                startTogether.await();
                return second.run();
            });
            startTogether.countDown();
            return List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private String attempt(Patient who, LocalDateTime startAt, int durationMinutes) {
        try {
            appointmentService.bookAppointment(who.getId(),
                    new AppointmentRequest(startAt, durationMinutes, "Race check"));
            return "BOOKED";
        } catch (AppointmentConflictException ex) {
            return "CONFLICT";
        }
    }

    @FunctionalInterface
    private interface Booking {
        String run() throws Exception;
    }
}
