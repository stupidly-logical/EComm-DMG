package com.ecomm.oms.order.dto;

import jakarta.validation.constraints.Size;

/**
 * Checkout input. All fields optional:
 * <ul>
 *   <li>{@code idempotencyKey} — supply to make a retried checkout return the original order
 *       instead of placing a second one; a random key is generated when absent.</li>
 *   <li>{@code paymentToken} — {@code "DECLINE"} forces the mock gateway to decline.</li>
 *   <li>{@code paymentMethod} — label recorded on the payment (defaults to MOCK_CARD).</li>
 * </ul>
 */
public record CheckoutRequest(
        @Size(max = 100) String idempotencyKey,
        @Size(max = 40) String paymentMethod,
        @Size(max = 60) String paymentToken) {
}
