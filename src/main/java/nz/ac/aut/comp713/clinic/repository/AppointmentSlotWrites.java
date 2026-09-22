package nz.ac.aut.comp713.clinic.repository;

import java.time.LocalDateTime;

/**
 * Atomic check-and-write statements for booking appointment slots.
 *
 * <p>This is the concurrency defence from the week-5 lab applied to bookings:
 * "a transaction is atomic, but it is not automatically concurrency-safe", so the
 * overlap <em>test</em> and the <em>write</em> are combined into one atomic database
 * statement, and the caller checks the number of affected rows. Both methods
 * return {@code 1} when the booking was written and {@code 0} when it overlaps an
 * existing appointment of the same patient (nothing is written in that case).</p>
 *
 * <p>The statements are parameterised SQL (week-3 lab style) executed over JDBC; see
 * {@link AppointmentSlotWritesImpl} for the exact SQL. They are one layer of a
 * layered defence: the service serialises concurrent changes per patient with a
 * pessimistic row lock (H2 has no range-exclusion constraint, so an atomic
 * statement alone cannot stop two overlapping ranges from racing), keeps a
 * fail-fast overlap check in front for clear errors, and the
 * {@code uk_appointment_patient_start} constraint is the final backstop for
 * identical start times.</p>
 */
public interface AppointmentSlotWrites {

    /**
     * Inserts the appointment only if the patient has no appointment overlapping
     * {@code [startAt, endAt)}.
     *
     * @return 1 if the row was inserted, 0 if the slot was already taken
     */
    int insertIfNoOverlap(Long patientId, LocalDateTime startAt, LocalDateTime endAt,
                          String reason, LocalDateTime createdAt);

    /**
     * Moves the given appointment (and updates its reason) only if no <em>other</em>
     * appointment of the same patient overlaps {@code [startAt, endAt)}. The booking
     * being moved is excluded from the overlap check.
     *
     * @return 1 if the row was updated, 0 if the new slot was already taken
     */
    int updateIfNoOverlap(Long id, Long patientId, LocalDateTime startAt, LocalDateTime endAt,
                          String reason);
}
