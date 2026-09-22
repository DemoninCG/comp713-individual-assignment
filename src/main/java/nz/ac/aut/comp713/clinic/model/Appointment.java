package nz.ac.aut.comp713.clinic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

/**
 * A booked appointment slot belonging to one patient.
 *
 * <p>Database rules (mirroring the week-5 lab's "the database is the final
 * protection" lesson):</p>
 * <ul>
 *   <li>{@code uk_appointment_patient_start} — a patient cannot be booked twice at
 *   the same start time (backstop when booking requests race);</li>
 *   <li>{@code ck_appointment_valid_range} — an appointment must end after it
 *   starts ({@code end_at > start_at}).</li>
 * </ul>
 *
 * <p>Overlapping (but not identical) slots cannot be expressed as a simple unique
 * constraint, so they are enforced by atomic check-and-write statements in
 * {@code AppointmentSlotWrites} — see that interface for details.</p>
 */
@Entity
@Table(name = "appointments",
        uniqueConstraints = @UniqueConstraint(name = "uk_appointment_patient_start",
                columnNames = {"patient_id", "start_at"}))
@Check(constraints = "end_at > start_at")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /**
     * Read-only mirror of the foreign-key column (the week-5 lab's {@code courseId}
     * pattern): it keeps {@code patient_id} as a directly mapped attribute so derived
     * queries such as {@code findByPatientIdAndStartAt} can resolve {@code patientId}
     * without initialising the lazy association.
     */
    @Column(name = "patient_id", insertable = false, updatable = false)
    private Long patientId;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(length = 200)
    private String reason;

    /** When the booking was made (clinic-local time); set once on insert. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Appointment() {
        // Required by JPA.
    }

    public Appointment(Patient patient, LocalDateTime startAt, LocalDateTime endAt, String reason) {
        this.patient = patient;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public Long getPatientId() {
        // Delegates to the association so newly created (not yet reloaded) instances
        // also report the id — same approach as the week-5 lab entity.
        return patient.getId();
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
