package com.ecomm.oms.payment;

import java.math.BigDecimal;

/**
 * Abstraction over an external payment processor. Implementations must be idempotent on
 * {@link ChargeRequest#idempotencyKey()} so a retried charge does not bill twice.
 */
public interface PaymentGateway {

    PaymentResult charge(ChargeRequest request);

    record ChargeRequest(String idempotencyKey, BigDecimal amount, String token, String method) {
    }

    record PaymentResult(boolean approved, String gatewayRef, String declineReason) {

        public static PaymentResult approved(String gatewayRef) {
            return new PaymentResult(true, gatewayRef, null);
        }

        public static PaymentResult declined(String reason) {
            return new PaymentResult(false, null, reason);
        }
    }
}
