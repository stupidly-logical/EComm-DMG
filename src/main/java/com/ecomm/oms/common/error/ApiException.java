package com.ecomm.oms.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base type for domain/application exceptions that map deterministically to an HTTP status
 * and an RFC-7807 {@code ProblemDetail}. Subclasses fix the status; the message becomes the
 * problem {@code detail} and {@link #getErrorCode()} the machine-readable {@code code}.
 */
public abstract class ApiException extends RuntimeException {

    protected ApiException(String message) {
        super(message);
    }

    /** HTTP status this exception maps to. */
    public abstract HttpStatus getStatus();

    /** Stable, machine-readable error code surfaced in the problem body. */
    public abstract String getErrorCode();
}
