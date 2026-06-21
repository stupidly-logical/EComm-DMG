package com.ecomm.oms.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Sets the absolute on-hand quantity for a product at a warehouse (creating the stock row if
 * absent). Reserved units are preserved; the new on-hand must not drop below them.
 */
public record StockAdjustmentRequest(
        @NotNull Long productId,
        @NotNull @PositiveOrZero Integer quantityOnHand) {
}
