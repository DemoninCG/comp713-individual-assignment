/**
 * Booking desk page: pick a patient, then book / reschedule / cancel appointments.
 *
 * The patient detail endpoint (GET /patients/{id}) embeds the appointments, which
 * shows the patient–appointment relationship in one round trip. All data is
 * rendered as text nodes (the XSS rule in app.js).
 */
import { api, clearBanner, el, formatDateTime, isoLocal, localToday, showApiError, showBanner, splitIso } from './app.js';

const patientSelect = document.querySelector('#patient-select');
const appointmentsBody = document.querySelector('#appointments-body');
const bookForm = document.querySelector('#book-form');
const rescheduleForm = document.querySelector('#reschedule-form');

let currentAppointments = [];
let reschedulingId = null;

// --- patients ---

async function loadPatients(preferredId) {
    const patients = await api.get('/patients');
    patientSelect.replaceChildren(...patients.map((patient) => el('option', { value: patient.id },
        `${patient.firstName} ${patient.lastName} (${patient.email})`)));
    if (preferredId && patients.some((patient) => String(patient.id) === String(preferredId))) {
        patientSelect.value = String(preferredId);
    }
    await loadAppointments();
}

// --- appointments ---

async function loadAppointments() {
    const patientId = patientSelect.value;
    if (!patientId) {
        currentAppointments = [];
        renderAppointments();
        return;
    }
    const detail = await api.get(`/patients/${patientId}`);
    currentAppointments = detail.appointments;
    renderAppointments();
}

function renderAppointments() {
    if (currentAppointments.length === 0) {
        appointmentsBody.replaceChildren(
            el('tr', { className: 'empty-row' },
                el('td', { colSpan: 3 }, 'No appointments booked for this patient.')));
        return;
    }
    appointmentsBody.replaceChildren(...currentAppointments.map((appointment) => el('tr', {},
        el('td', {}, `${formatDateTime(appointment.startAt)} – ${appointment.endAt.slice(11, 16)}`),
        el('td', {}, appointment.reason || '—'),
        el('td', {},
            el('button', { className: 'btn btn--secondary btn--small', type: 'button',
                    onClick: () => startReschedule(appointment) }, 'Reschedule'),
            el('button', { className: 'btn btn--danger btn--small', type: 'button',
                    onClick: () => cancelAppointment(appointment) }, 'Cancel')),
    )));
}

// --- book ---

bookForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearBanner();
    try {
        const created = await api.post(`/patients/${patientSelect.value}/appointments`, {
            startAt: isoLocal(bookForm.querySelector('#book-date').value, bookForm.querySelector('#book-time').value),
            durationMinutes: Number(bookForm.querySelector('#book-duration').value),
            reason: bookForm.querySelector('#book-reason').value || null,
        });
        showBanner('success', `Booked appointment #${created.id} — ${formatDateTime(created.startAt)}.`);
        bookForm.reset();
        bookForm.querySelector('#book-duration').value = 30;
        await loadAppointments();
    } catch (error) {
        showApiError(error);
    }
});

// --- reschedule ---

function startReschedule(appointment) {
    clearBanner();
    reschedulingId = appointment.id;
    const start = splitIso(appointment.startAt);
    const end = splitIso(appointment.endAt);
    rescheduleForm.querySelector('#reschedule-id').textContent = `#${appointment.id}`;
    rescheduleForm.querySelector('#reschedule-date').value = start.date;
    rescheduleForm.querySelector('#reschedule-time').value = start.time;
    rescheduleForm.querySelector('#reschedule-duration').value =
        minutesBetween(start, end);
    rescheduleForm.querySelector('#reschedule-reason').value = appointment.reason || '';
    rescheduleForm.hidden = false;
    rescheduleForm.scrollIntoView({ block: 'nearest' });
}

rescheduleForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearBanner();
    try {
        const updated = await api.put(`/appointments/${reschedulingId}`, {
            startAt: isoLocal(rescheduleForm.querySelector('#reschedule-date').value,
                rescheduleForm.querySelector('#reschedule-time').value),
            durationMinutes: Number(rescheduleForm.querySelector('#reschedule-duration').value),
            reason: rescheduleForm.querySelector('#reschedule-reason').value || null,
        });
        showBanner('success', `Appointment #${updated.id} moved to ${formatDateTime(updated.startAt)}.`);
        closeReschedule();
        await loadAppointments();
    } catch (error) {
        showApiError(error);
    }
});

document.querySelector('#reschedule-keep').addEventListener('click', closeReschedule);

function closeReschedule() {
    reschedulingId = null;
    rescheduleForm.hidden = true;
    rescheduleForm.reset();
}

// --- cancel ---

async function cancelAppointment(appointment) {
    clearBanner();
    if (!window.confirm(`Cancel the appointment on ${formatDateTime(appointment.startAt)}?`)) {
        return;
    }
    try {
        await api.del(`/appointments/${appointment.id}`);
        showBanner('success', `Appointment #${appointment.id} cancelled.`);
        closeReschedule();
        await loadAppointments();
    } catch (error) {
        showApiError(error);
    }
}

// --- helpers + startup ---

function minutesBetween(start, end) {
    return (Number(end.time.slice(0, 2)) * 60 + Number(end.time.slice(3, 5)))
        - (Number(start.time.slice(0, 2)) * 60 + Number(start.time.slice(3, 5)));
}

patientSelect.addEventListener('change', () => {
    closeReschedule();
    loadAppointments().catch(showApiError);
});

bookForm.querySelector('#book-date').min = localToday();
rescheduleForm.querySelector('#reschedule-date').min = localToday();

loadPatients(new URLSearchParams(window.location.search).get('patientId')).catch(showApiError);
