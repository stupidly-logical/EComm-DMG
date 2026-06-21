package com.ecomm.oms.common.error;

import org.springframework.http.HttpStatus;

/**
 * Raised when input is syntactically valid but violates a business rule
 * (e.g. coupon below minimum order, expired coupon). Maps to HTTP 422.
 */
public class BusinessRuleException extends ApiException {

    private final String errorCode;

    public BusinessRuleException(String message) {
        this(message, "BUSINESS_RULE_VIOLATION");
    }

    public BusinessRuleException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
