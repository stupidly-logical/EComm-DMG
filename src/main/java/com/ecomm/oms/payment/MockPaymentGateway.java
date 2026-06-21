package com.ecomm.oms.payment;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process payment gateway for development and tests. Outcome is configurable per request:
 * a token of {@code "DECLINE"} (case-insensitive) is declined, anything else is approved. The
 * result is memoised by idempotency key so a replay returns the original outcome without
 * "charging" again.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    static final String DECLINE_TOKEN = "DECLINE";

    private final ConcurrentHashMap<String, PaymentResult> byIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public PaymentResult charge(ChargeRequest request) {
        return byIdempotencyKey.computeIfAbsent(request.idempotencyKey(), key -> {
            if (request.token() != null && DECLINE_TOKEN.equalsIgnoreCase(request.token().trim())) {
                return PaymentResult.declined("Card was declined");
            }
            return PaymentResult.approved("MOCK-" + UUID.randomUUID());
        });
    }
}
