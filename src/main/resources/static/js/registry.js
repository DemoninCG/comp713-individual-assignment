/**
 * Patient registry page: register patients, filter by last name, and link into
 * the booking desk for each patient. All data is rendered as text nodes (see the
 * XSS rule in app.js).
 */
import { api, clearBanner, el, showApiError, showBanner } from './app.js';

const patientsBody = document.querySelector('#patients-body');

async function loadPatients(lastName) {
    const query = lastName ? `?lastName=${encodeURIComponent(lastName)}` : '';
    const patients = await api.get(`/patients${query}`);
    renderPatients(patients);
}

function renderPatients(patients) {
    if (patients.length === 0) {
        patientsBody.replaceChildren(
            el('tr', { className: 'empty-row' },
                el('td', { colSpan: 4 }, 'No patients match this view.')));
        return;
    }
    patientsBody.replaceChildren(...patients.map((patient) => el('tr', {},
        el('td', {}, `${patient.firstName} ${patient.lastName}`),
        el('td', { className: 'mono' }, patient.email),
        el('td', {}, patient.phone || '—'),
        el('td', {},
            el('a', { className: 'btn btn--secondary btn--small', href: `appointments.html?patientId=${patient.id}` },
                'Book →')),
    )));
}

document.querySelector('#register-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    clearBanner();
    const form = event.target;
    try {
        const created = await api.post('/patients', {
            firstName: form.firstName.value,
            lastName: form.lastName.value,
            email: form.email.value,
            phone: form.phone.value || null,
        });
        showBanner('success', `Registered ${created.firstName} ${created.lastName} (patient #${created.id}).`);
        form.reset();
        await loadPatients(document.querySelector('#filter-last-name').value.trim());
    } catch (error) {
        showApiError(error);
    }
});

document.querySelector('#filter-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    clearBanner();
    try {
        await loadPatients(document.querySelector('#filter-last-name').value.trim());
    } catch (error) {
        showApiError(error);
    }
});

loadPatients().catch(showApiError);
