package com.ecomm.oms.pricing;

import com.ecomm.oms.common.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure, deterministic order arithmetic — no Spring, no database. Given priced lines (each
 * with a per-unit price, quantity, and tax-rate percent) and an already-validated order-level
 * discount, it allocates the discount across lines, computes per-line tax on the discounted
 * base, and reconciles the totals.
 *
 * <p>Two correctness guarantees the unit tests pin down:
 * <ul>
 *   <li>The sum of per-line discounts equals the order discount exactly — any rounding
 *       remainder is absorbed by the last line, so totals never drift by a cent.</li>
 *   <li>Every monetary value is at {@link Money#SCALE} with HALF_UP rounding.</li>
 * </ul>
 */
public final class PricingCalculator {

    /** A single line to be priced. */
    public record Line(BigDecimal unitPrice, int quantity, BigDecimal taxRatePercent) {

        public BigDecimal subtotal() {
            return Money.multiply(unitPrice, quantity);
        }
    }

    /** Computed figures for one line. */
    public record PricedLine(
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineSubtotal,
            BigDecimal lineDiscount,
            BigDecimal lineTax,
            BigDecimal lineTotal) {
    }

    /** Computed figures for the whole order (excluding shipping, added by the service). */
    public record Result(
            List<PricedLine> lines,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal taxTotal,
            BigDecimal grandTotal) {
    }

    private PricingCalculator() {
    }

    /**
     * @param lines         priced lines (may be empty → all-zero result)
     * @param orderDiscount total discount to spread across lines; clamped to [0, subtotal]
     */
    public static Result price(List<Line> lines, BigDecimal orderDiscount) {
        BigDecimal subtotal = lines.stream()
                .map(Line::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        subtotal = Money.scale(subtotal);

        BigDecimal discount = clampDiscount(orderDiscount, subtotal);

        List<PricedLine> priced = allocate(lines, subtotal, discount);

        BigDecimal taxTotal = priced.stream()
                .map(PricedLine::lineTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        taxTotal = Money.scale(taxTotal);

        BigDecimal grandTotal = Money.scale(subtotal.subtract(discount).add(taxTotal));

        return new Result(priced, subtotal, discount, taxTotal, grandTotal);
    }

    private static BigDecimal clampDiscount(BigDecimal discount, BigDecimal subtotal) {
        if (discount == null || discount.signum() <= 0) {
            return Money.ZERO;
        }
        BigDecimal scaled = Money.scale(discount);
        return scaled.compareTo(subtotal) > 0 ? subtotal : scaled;
    }

    private static List<PricedLine> allocate(List<Line> lines, BigDecimal subtotal,
                                             BigDecimal orderDiscount) {
        List<PricedLine> result = new ArrayList<>(lines.size());
        BigDecimal allocatedDiscount = Money.ZERO;
        boolean spreadDiscount = orderDiscount.signum() > 0 && subtotal.signum() > 0;

        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            BigDecimal lineSubtotal = line.subtotal();

            BigDecimal lineDiscount;
            if (!spreadDiscount) {
                lineDiscount = Money.ZERO;
            } else if (i == lines.size() - 1) {
                // Last line absorbs the rounding remainder so the sum is exact.
                lineDiscount = Money.scale(orderDiscount.subtract(allocatedDiscount));
            } else {
                lineDiscount = Money.scale(
                        orderDiscount.multiply(lineSubtotal).divide(subtotal, Money.SCALE, Money.ROUNDING));
                allocatedDiscount = allocatedDiscount.add(lineDiscount);
            }

            BigDecimal taxableBase = Money.atLeastZero(lineSubtotal.subtract(lineDiscount));
            BigDecimal lineTax = Money.percentageOf(taxableBase, safeRate(line.taxRatePercent()));
            BigDecimal lineTotal = Money.scale(lineSubtotal.subtract(lineDiscount).add(lineTax));

            result.add(new PricedLine(
                    Money.scale(line.unitPrice()), line.quantity(),
                    Money.scale(lineSubtotal), lineDiscount, lineTax, lineTotal));
        }
        return result;
    }

    private static BigDecimal safeRate(BigDecimal rate) {
        return rate == null ? BigDecimal.ZERO : rate;
    }
}
