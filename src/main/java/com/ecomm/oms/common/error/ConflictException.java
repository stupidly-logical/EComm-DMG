package com.ecomm.oms.common.error;

import org.springframework.http.HttpStatus;

/**
 * Raised on a conflict with current resource state: illegal state-machine transition,
 * insufficient stock, duplicate unique key, etc. Maps to HTTP 409.
 */
public class ConflictException extends ApiException {

    private final String errorCode;

    public ConflictException(String message) {
        this(message, "CONFLICT");
    }

    public ConflictException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
