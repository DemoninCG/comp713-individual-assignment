package nz.ac.aut.comp713.clinic.controller;

/**
 * The single error model returned by every failed request (the week-6 lab's error
 * contract): a stable machine-readable {@code code}, a human-readable
 * {@code message}, and the {@code path} of the failed request.
 */
public record ApiError(String code, String message, String path) {
}
