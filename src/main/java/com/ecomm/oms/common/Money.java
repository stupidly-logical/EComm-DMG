package com.ecomm.oms.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money helpers. All monetary values are {@link BigDecimal} scaled to 2 decimal places with
 * HALF_UP rounding — the single rounding convention used across pricing, tax, and refunds so
 * line totals reconcile deterministically.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = scale(BigDecimal.ZERO);

    private Money() {
    }

    /** Normalise to the canonical money scale/rounding. */
    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal multiply(BigDecimal amount, int quantity) {
        return scale(amount.multiply(BigDecimal.valueOf(quantity)));
    }

    /** Applies a percentage (e.g. 8.5 → 8.5%) to an amount, rounded to money scale. */
    public static BigDecimal percentageOf(BigDecimal amount, BigDecimal percent) {
        return scale(amount.multiply(percent).divide(BigDecimal.valueOf(100)));
    }

    /** Clamps a (possibly negative) amount to a minimum of zero, scaled. */
    public static BigDecimal atLeastZero(BigDecimal value) {
        BigDecimal scaled = scale(value);
        return scaled.signum() < 0 ? ZERO : scaled;
    }
}
