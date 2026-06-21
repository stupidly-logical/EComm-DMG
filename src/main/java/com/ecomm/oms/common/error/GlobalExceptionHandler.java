package com.ecomm.oms.common.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates application and framework exceptions into a consistent RFC-7807
 * {@link ProblemDetail} response. Every body carries a stable {@code code}, a
 * {@code timestamp}, and — for validation failures — a list of field {@code errors}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Domain/application exceptions with an explicit status mapping. */
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        return problem(ex.getStatus(), ex.getErrorCode(), ex.getMessage());
    }

    /** Bean-validation failures on @Valid @RequestBody arguments. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldError)
                .collect(Collectors.toList());
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more fields are invalid");
        pd.setProperty("errors", errors);
        return pd;
    }

    /** Constraint violations on @Validated method parameters (path/query params). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of(
                        "field", v.getPropertyPath().toString(),
                        "message", v.getMessage()))
                .collect(Collectors.toList());
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more parameters are invalid");
        pd.setProperty("errors", errors);
        return pd;
    }

    /** Malformed / unparseable request body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is malformed");
    }

    /**
     * Method-security ({@code @PreAuthorize}) denials surface here because Spring MVC's
     * exception resolver handles them during controller invocation, before they can reach
     * the security filter's access-denied handler.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action");
    }

    /**
     * Database constraint breaches that slipped past service-level checks (FK-referenced
     * deletes, unique-key races). Reported as a conflict rather than a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "CONSTRAINT_VIOLATION",
                "The operation conflicts with related data");
    }

    /** Bad arguments surfaced from service-layer guard clauses. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    /** Catch-all: never leak internals to the client. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    private static Map<String, String> fieldError(FieldError fe) {
        return Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
    }
}
