package com.ecomm.oms.common.error;

import org.springframework.http.HttpStatus;

/** Raised on failed authentication (e.g. bad credentials). Maps to HTTP 401. */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNAUTHORIZED;
    }

    @Override
    public String getErrorCode() {
        return "UNAUTHENTICATED";
    }
}
