package com.ecomm.oms.order;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle. The enum owns its transition graph (per the state-machine-transition
 * skill); warehouse staff drive CONFIRMED→…→DELIVERED, returns open the RETURNED path, and
 * either of the early states may be CANCELLED.
 */
public enum OrderStatus {
    PLACED,
    CONFIRMED,
    PACKED,
    SHIPPED,
    DELIVERED,
    RETURNED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PLACED, EnumSet.of(CONFIRMED, CANCELLED),
            CONFIRMED, EnumSet.of(PACKED, CANCELLED),
            PACKED, EnumSet.of(SHIPPED),
            SHIPPED, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.of(RETURNED),
            RETURNED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class));

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
