package com.ecomm.oms.order;

/**
 * Published inside the checkout transaction once an order is placed. Handled
 * {@code AFTER_COMMIT} on an async executor by the downstream pipeline (fulfillment routing,
 * notification, audit) so the customer's checkout response never waits on them.
 */
public record OrderPlacedEvent(Long orderId, Long customerId) {
}
