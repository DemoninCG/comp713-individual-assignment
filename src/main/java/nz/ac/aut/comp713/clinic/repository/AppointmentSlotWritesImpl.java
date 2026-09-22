package nz.ac.aut.comp713.clinic.repository;

import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC implementation of the atomic slot statements (a repository may use JDBC,
 * JPA or any other storage technology — week-5 lecture).
 *
 * <p>Both statements test for an overlapping appointment of the same patient and
 * write in one atomic step, so two concurrent bookings cannot both succeed even if
 * they pass any application-level checks at the same time. Overlap is defined as
 * {@code existing.start_at < newEnd AND existing.end_at > newStart} (touching
 * end-to-start is allowed, i.e. back-to-back bookings).</p>
 *
 * <p>The casts give the database the parameter types explicitly so statements stay
 * predictable for every value (including {@code NULL} for a missing reason).</p>
 */
class AppointmentSlotWritesImpl implements AppointmentSlotWrites {

    private static final String INSERT_IF_NO_OVERLAP = """
            INSERT INTO appointments (patient_id, start_at, end_at, reason, created_at)
            SELECT CAST(? AS BIGINT), CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP),
                   CAST(? AS VARCHAR(200)), CAST(? AS TIMESTAMP)
            FROM (VALUES (0)) AS single_row
            WHERE NOT EXISTS (
                SELECT 1 FROM appointments
                 WHERE patient_id = ?
                   AND start_at < ?
                   AND end_at > ?
            )
            """;

    private static final String UPDATE_IF_NO_OVERLAP = """
            UPDATE appointments
               SET start_at = ?, end_at = ?, reason = ?
             WHERE id = ?
               AND NOT EXISTS (
                   SELECT 1 FROM appointments a
                    WHERE a.patient_id = ?
                      AND a.id <> ?
                      AND a.start_at < ?
                      AND a.end_at > ?
               )
            """;

    private final JdbcTemplate jdbc;

    public AppointmentSlotWritesImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int insertIfNoOverlap(Long patientId, LocalDateTime startAt, LocalDateTime endAt,
                                 String reason, LocalDateTime createdAt) {
        return jdbc.update(INSERT_IF_NO_OVERLAP,
                patientId, startAt, endAt, reason, createdAt,
                patientId, endAt, startAt);
    }

    @Override
    public int updateIfNoOverlap(Long id, Long patientId, LocalDateTime startAt, LocalDateTime endAt,
                                 String reason) {
        return jdbc.update(UPDATE_IF_NO_OVERLAP,
                startAt, endAt, reason,
                id,
                patientId, id, endAt, startAt);
    }
}
