package com.ecomm.oms.fulfillment.dto;

import com.ecomm.oms.order.OrderStatus;
import jakarta.validation.constraints.NotNull;

/** Target order status for a warehouse-staff fulfillment step (PACKED, SHIPPED, DELIVERED). */
public record FulfillmentStatusRequest(@NotNull OrderStatus status) {
}
