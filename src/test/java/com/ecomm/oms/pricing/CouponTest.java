package com.ecomm.oms.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Domain-logic tests for {@link Coupon}: discount computation, validity window, and caps.
 */
class CouponTest {

    private Coupon percent(BigDecimal value, BigDecimal minOrder) {
        return new Coupon("P", CouponType.PERCENT, value, minOrder, null, null, null, true);
    }

    @Test
    void percentDiscountIsAPercentageOfSubtotal() {
        assertThat(percent(new BigDecimal("15"), BigDecimal.ZERO).discountFor(new BigDecimal("200.00")))
                .isEqualByComparingTo("30.00");
    }

    @Test
    void fixedDiscountIsClampedToSubtotal() {
        Coupon fixed = new Coupon("F", CouponType.FIXED, new BigDecimal("50.00"),
                BigDecimal.ZERO, null, null, null, true);

        assertThat(fixed.discountFor(new BigDecimal("30.00"))).isEqualByComparingTo("30.00");
        assertThat(fixed.discountFor(new BigDecimal("80.00"))).isEqualByComparingTo("50.00");
    }

    @Test
    void meetsMinimumComparesAgainstSubtotal() {
        Coupon c = percent(new BigDecimal("10"), new BigDecimal("100.00"));
        assertThat(c.meetsMinimum(new BigDecimal("99.99"))).isFalse();
        assertThat(c.meetsMinimum(new BigDecimal("100.00"))).isTrue();
    }

    @Test
    void inactiveCouponIsNotRedeemable() {
        Coupon c = new Coupon("X", CouponType.PERCENT, new BigDecimal("10"),
                BigDecimal.ZERO, null, null, null, false);
        assertThat(c.isRedeemable(Instant.now())).isFalse();
    }

    @Test
    void expiredCouponIsNotRedeemable() {
        Instant now = Instant.now();
        Coupon c = new Coupon("X", CouponType.PERCENT, new BigDecimal("10"), BigDecimal.ZERO,
                now.minus(10, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), null, true);
        assertThat(c.isRedeemable(now)).isFalse();
    }

    @Test
    void notYetValidCouponIsNotRedeemable() {
        Instant now = Instant.now();
        Coupon c = new Coupon("X", CouponType.PERCENT, new BigDecimal("10"), BigDecimal.ZERO,
                now.plus(1, ChronoUnit.DAYS), null, null, true);
        assertThat(c.isRedeemable(now)).isFalse();
    }

    @Test
    void redemptionCapIsEnforced() {
        Coupon c = new Coupon("X", CouponType.PERCENT, new BigDecimal("10"),
                BigDecimal.ZERO, null, null, 1, true);
        assertThat(c.isRedeemable(Instant.now())).isTrue();
        c.redeem();
        assertThat(c.isRedeemable(Instant.now())).isFalse();
    }
}
