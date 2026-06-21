package com.ecomm.oms.common.error;

import org.springframework.http.HttpStatus;

/** Raised when a referenced resource does not exist. Maps to HTTP 404. */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(message);
    }

    /** Convenience for the common "{Type} {id} not found" shape. */
    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException(resource + " " + id + " not found");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }

    @Override
    public String getErrorCode() {
        return "NOT_FOUND";
    }
}
