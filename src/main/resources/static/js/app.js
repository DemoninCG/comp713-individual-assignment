/**
 * Shared client helpers for the Clinic Appointment System.
 *
 * XSS rule (week-2 web-servers lecture): all dynamic data reaches the page as text
 * nodes — `el()` uses createElement/createTextNode and never innerHTML — so user
 * input such as names or reasons can never be executed as markup.
 */

const API_BASE = '/api/v1';

/** Builds a DOM element with properties and text children. */
export function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [name, value] of Object.entries(props)) {
        if (value == null) continue;
        if (name === 'className') {
            node.className = value;
        } else if (name.startsWith('on') && typeof value === 'function') {
            node.addEventListener(name.slice(2).toLowerCase(), value);
        } else if (name in node && typeof value !== 'object') {
            node[name] = value;               // value, type, placeholder, hidden, ...
        } else {
            node.setAttribute(name, value);   // aria-*, data-*, ...
        }
    }
    for (const child of children.flat(Infinity)) {
        if (child == null) continue;
        node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
}

/** Minimal JSON API client. Throws {status, code, message, path} on failures. */
async function request(path, options = {}) {
    const response = await fetch(API_BASE + path, {
        headers: { 'Content-Type': 'application/json' },
        ...options,
    });
    if (response.status === 204) return null;
    const body = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error(body && body.message ? body.message : `Request failed with status ${response.status}`);
        error.status = response.status;
        error.code = body && body.code ? body.code : 'UNEXPECTED_ERROR';
        error.path = body && body.path ? body.path : path;
        throw error;
    }
    return body;
}

export const api = {
    get: (path) => request(path),
    post: (path, body) => request(path, { method: 'POST', body: JSON.stringify(body) }),
    put: (path, body) => request(path, { method: 'PUT', body: JSON.stringify(body) }),
    del: (path) => request(path, { method: 'DELETE' }),
};

/** Shows a success or error banner (with the ApiError code badge on failures). */
export function showBanner(kind, message, code) {
    const banner = document.querySelector('#banner');
    banner.className = `banner banner--${kind}`;
    // Note: replaceChildren stringifies non-Node arguments (a bare null would
    // become the text "null"), so only element nodes are passed.
    const parts = [];
    if (code) {
        parts.push(el('span', { className: 'banner__code' }, code));
    }
    parts.push(el('span', {}, message));
    banner.replaceChildren(...parts);
    banner.hidden = false;
}

export function clearBanner() {
    const banner = document.querySelector('#banner');
    banner.hidden = true;
    banner.replaceChildren();
}

/** Shows an error thrown by the api client (code + message from ApiError). */
export function showApiError(error) {
    showBanner('error', error.message || 'The request failed.', error.code);
}

const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * Formats an ISO local timestamp such as 2026-09-23T09:30:00 as
 * "Wed 23 Sep 2026, 09:30". Parsed from parts — times are clinic-local and must
 * not shift through a timezone conversion.
 */
export function formatDateTime(iso) {
    if (!iso) return '';
    const [datePart, timePart = '00:00'] = iso.split('T');
    const [year, month, day] = datePart.split('-').map(Number);
    const [hour, minute] = timePart.split(':').map(Number);
    const weekday = new Date(year, month - 1, day).getDay();
    const hh = String(hour).padStart(2, '0');
    const mm = String(minute).padStart(2, '0');
    return `${DAYS[weekday]} ${day} ${MONTHS[month - 1]} ${year}, ${hh}:${mm}`;
}

/** Combines <input type="date"> + <input type="time"> into an ISO local timestamp. */
export function isoLocal(dateValue, timeValue) {
    return `${dateValue}T${timeValue}:00`;
}

/** Splits an ISO local timestamp back into date/time input values. */
export function splitIso(iso) {
    return { date: iso.slice(0, 10), time: iso.slice(11, 16) };
}

/** Today's date as a yyyy-MM-dd value for <input type="date"> min attributes. */
export function localToday() {
    const now = new Date();
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const dd = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${mm}-${dd}`;
}
