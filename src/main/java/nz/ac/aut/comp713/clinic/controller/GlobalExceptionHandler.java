package nz.ac.aut.comp713.clinic.controller;

import nz.ac.aut.comp713.clinic.service.PatientExistsException;
import nz.ac.aut.comp713.clinic.service.PatientNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

import java.util.stream.Collectors;

/**
 * Translates every failure into the single {@link ApiError} error model
 * {code, message, path} (the week-6 lab's error contract), so clients get a
 * consistent shape with stable machine-readable codes:
 *
 * <ul>
 *   <li>400 {@code INVALID_REQUEST} — bean validation, malformed JSON, bad parameter types</li>
 *   <li>415 {@code UNSUPPORTED_MEDIA_TYPE} — wrong request Content-Type</li>
 *   <li>404 {@code PATIENT_NOT_FOUND}, 409 {@code PATIENT_EXISTS}</li>
 *   <li>500 {@code INTERNAL_ERROR} — generic message; no stack traces leaked</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", fields, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformedJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request body is missing or is not valid JSON", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> parameterTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                          HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Parameter '" + ex.getName() + "' has an invalid value", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                         HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type must be application/json", request);
    }

    @ExceptionHandler(PatientNotFoundException.class)
    public ResponseEntity<ApiError> patientNotFound(PatientNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(PatientExistsException.class)
    public ResponseEntity<ApiError> patientExists(PatientExistsException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "PATIENT_EXISTS", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message,
                                           HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiError(code, message, request.getRequestURI()));
    }
}
