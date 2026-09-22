package nz.ac.aut.comp713.clinic.config;

import nz.ac.aut.comp713.clinic.model.Appointment;
import nz.ac.aut.comp713.clinic.model.Patient;
import nz.ac.aut.comp713.clinic.repository.AppointmentRepository;
import nz.ac.aut.comp713.clinic.repository.PatientRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * Seeds a small demo data set at startup (the week-5 lab's demo-data pattern).
 *
 * <p>The database is created fresh on every start (embedded H2, create-drop), so a
 * few patients and upcoming appointments make the client pages and the
 * demonstration immediately meaningful. All seeded slots sit in the future on
 * weekday mornings/afternoons so they obey the booking rules the API enforces.</p>
 */
@Configuration
public class DemoDataConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DemoDataConfiguration.class);

    @Bean
    CommandLineRunner loadDemoData(PatientRepository patients, AppointmentRepository appointments) {
        return args -> {
            if (patients.count() > 0) {
                return; // already seeded (e.g. tests ran first)
            }

            Patient aroha = patients.save(new Patient("Aroha", "Ngata", "aroha.ngata@example.com", "021 555 0101"));
            Patient sam = patients.save(new Patient("Sam", "Whitcombe", "sam.whitcombe@example.com", "021 555 0102"));
            Patient lee = patients.save(new Patient("Lee", "Chen", "lee.chen@example.com", null));

            appointments.save(new Appointment(aroha, at(1, 9, 30), at(1, 10, 0), "Annual check-up"));
            appointments.save(new Appointment(aroha, at(5, 14, 0), at(5, 14, 30), "Follow-up: blood test results"));
            appointments.save(new Appointment(sam, at(2, 11, 0), at(2, 11, 45), "Sports injury assessment"));
            appointments.save(new Appointment(lee, at(3, 15, 30), at(3, 16, 0), "Vaccination"));

            log.info("Seeded {} demo patients and {} demo appointments", patients.count(), appointments.count());
        };
    }

    /**
     * The given clock time on the n-th upcoming working day (weekends are skipped so
     * the demo bookings always fall inside the clinic's opening hours).
     */
    private static LocalDateTime at(int workingDaysAhead, int hour, int minute) {
        LocalDateTime slot = LocalDateTime.now()
                .withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        for (int added = 0; added < workingDaysAhead;) {
            slot = slot.plusDays(1);
            if (slot.getDayOfWeek() != DayOfWeek.SATURDAY && slot.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return slot;
    }
}
