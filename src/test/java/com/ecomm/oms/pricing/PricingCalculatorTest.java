package com.ecomm.oms.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure arithmetic tests for {@link PricingCalculator}: discount allocation, tax on the
 * discounted base, rounding, and total reconciliation.
 */
class PricingCalculatorTest {

    private static PricingCalculator.Line line(String unitPrice, int qty, String ratePercent) {
        return new PricingCalculator.Line(new BigDecimal(unitPrice), qty, new BigDecimal(ratePercent));
    }

    @Test
    void emptyOrderIsAllZero() {
        var result = PricingCalculator.price(List.of(), BigDecimal.ZERO);

        assertThat(result.subtotal()).isEqualByComparingTo("0.00");
        assertThat(result.discountTotal()).isEqualByComparingTo("0.00");
        assertThat(result.taxTotal()).isEqualByComparingTo("0.00");
        assertThat(result.grandTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void computesSubtotalAndTaxWithNoDiscount() {
        var result = PricingCalculator.price(
                List.of(line("19.99", 2, "10")), BigDecimal.ZERO);

        assertThat(result.subtotal()).isEqualByComparingTo("39.98");
        assertThat(result.taxTotal()).isEqualByComparingTo("4.00"); // 39.98 * 10% = 3.998 -> 4.00
        assertThat(result.grandTotal()).isEqualByComparingTo("43.98");
    }

    @Test
    void taxIsComputedOnTheDiscountedBase() {
        // 2 lines, subtotal 200; 50 off (25%). Each line discounted 25.
        var result = PricingCalculator.price(
                List.of(line("100.00", 1, "10"), line("100.00", 1, "0")),
                new BigDecimal("50.00"));

        assertThat(result.subtotal()).isEqualByComparingTo("200.00");
        assertThat(result.discountTotal()).isEqualByComparingTo("50.00");
        // Line A: (100-25) * 10% = 7.50; Line B: no tax.
        assertThat(result.taxTotal()).isEqualByComparingTo("7.50");
        assertThat(result.grandTotal()).isEqualByComparingTo("157.50"); // 200 - 50 + 7.50
        assertThat(result.lines().get(0).lineDiscount()).isEqualByComparingTo("25.00");
        assertThat(result.lines().get(1).lineDiscount()).isEqualByComparingTo("25.00");
    }

    @Test
    void discountIsClampedToSubtotal() {
        var result = PricingCalculator.price(
                List.of(line("30.00", 1, "0")), new BigDecimal("100.00"));

        assertThat(result.discountTotal()).isEqualByComparingTo("30.00");
        assertThat(result.grandTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void perLineDiscountsSumExactlyToOrderDiscountDespiteRounding() {
        // 3 equal lines of 10 (subtotal 30), discount 10 -> 3.3333.. each.
        var result = PricingCalculator.price(
                List.of(line("10.00", 1, "0"), line("10.00", 1, "0"), line("10.00", 1, "0")),
                new BigDecimal("10.00"));

        var discounts = result.lines().stream().map(PricingCalculator.PricedLine::lineDiscount).toList();
        assertThat(discounts.get(0)).isEqualByComparingTo("3.33");
        assertThat(discounts.get(1)).isEqualByComparingTo("3.33");
        assertThat(discounts.get(2)).isEqualByComparingTo("3.34"); // remainder absorbed
        BigDecimal sum = discounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("10.00");
        assertThat(result.discountTotal()).isEqualByComparingTo("10.00");
    }

    @Test
    void taxRoundsHalfUpToCents() {
        var result = PricingCalculator.price(
                List.of(line("10.00", 1, "8.25")), BigDecimal.ZERO);

        // 10.00 * 8.25% = 0.825 -> 0.83
        assertThat(result.taxTotal()).isEqualByComparingTo("0.83");
        assertThat(result.grandTotal()).isEqualByComparingTo("10.83");
    }
}
