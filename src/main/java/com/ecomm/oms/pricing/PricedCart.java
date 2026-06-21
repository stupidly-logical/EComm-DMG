package com.ecomm.oms.pricing;

import com.ecomm.oms.catalog.Product;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fully-priced view of a cart: per-line money breakdown plus order totals and the coupon (if
 * any) that was actually applied. Shared by the cart preview response and checkout (which
 * turns each line into an OrderItem and redeems {@link #appliedCoupon()}).
 */
public record PricedCart(
        List<Line> lines,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal shippingTotal,
        BigDecimal grandTotal,
        Coupon appliedCoupon) {

    public record Line(
            Product product,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineSubtotal,
            BigDecimal lineDiscount,
            BigDecimal lineTax,
            BigDecimal lineTotal) {
    }
}
