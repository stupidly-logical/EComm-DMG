package com.ecomm.oms.returns;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Return-request lifecycle (state-machine-transition skill). A request is REQUESTED, then
 * either REJECTED or APPROVED; approval issues the refund and moves it to REFUNDED.
 */
public enum ReturnStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    REFUNDED;

    private static final Map<ReturnStatus, Set<ReturnStatus>> ALLOWED = Map.of(
            REQUESTED, EnumSet.of(APPROVED, REJECTED),
            APPROVED, EnumSet.of(REFUNDED),
            REJECTED, EnumSet.noneOf(ReturnStatus.class),
            REFUNDED, EnumSet.noneOf(ReturnStatus.class));

    public boolean canTransitionTo(ReturnStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
