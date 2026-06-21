package com.ecomm.oms.common.error;

import org.springframework.http.HttpStatus;

/** Raised when the payment gateway declines a charge. Maps to HTTP 402. */
public class PaymentDeclinedException extends ApiException {

    public PaymentDeclinedException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.PAYMENT_REQUIRED;
    }

    @Override
    public String getErrorCode() {
        return "PAYMENT_DECLINED";
    }
}
