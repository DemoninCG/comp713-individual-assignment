package nz.ac.aut.comp713.clinic.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A registered clinic patient.
 *
 * <p>Mirrors the week-5 lab entity style: JPA uses the protected no-arg constructor,
 * identity generation assigns the id, and database rules are declared as named
 * constraints ({@code uk_patient_email}). State changes happen in the service layer,
 * so the entity only exposes getters.</p>
 */
@Entity
@Table(name = "patients",
        uniqueConstraints = @UniqueConstraint(name = "uk_patient_email", columnNames = "email"))
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    /** When the patient was registered (clinic-local time); set once on insert. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * The patient's appointments. Cascading delete keeps the relationship consistent:
     * removing a patient removes their appointments (the FK lives on the child table).
     */
    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startAt ASC")
    private List<Appointment> appointments = new ArrayList<>();

    protected Patient() {
        // Required by JPA.
    }

    public Patient(String firstName, String lastName, String email, String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
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

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<Appointment> getAppointments() {
        return List.copyOf(appointments);
    }
}
