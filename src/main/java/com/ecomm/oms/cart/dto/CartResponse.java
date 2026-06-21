package com.ecomm.oms.cart.dto;

import com.ecomm.oms.cart.Cart;
import com.ecomm.oms.pricing.PricedCart;

import java.math.BigDecimal;
import java.util.List;

/**
 * The cart with its fully-priced breakdown. Built from the {@link Cart} (identity/status) and
 * the {@link PricedCart} computed by the pricing service.
 */
public record CartResponse(
        Long id,
        String status,
        String appliedCouponCode,
        List<CartLine> items,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal shippingTotal,
        BigDecimal grandTotal) {

    public record CartLine(
            Long productId,
            String sku,
            String name,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineSubtotal,
            BigDecimal lineDiscount,
            BigDecimal lineTax,
            BigDecimal lineTotal) {
    }

    public static CartResponse of(Cart cart, PricedCart priced) {
        List<CartLine> lines = priced.lines().stream()
                .map(l -> new CartLine(
                        l.product().getId(),
                        l.product().getSku(),
                        l.product().getName(),
                        l.quantity(),
                        l.unitPrice(),
                        l.lineSubtotal(),
                        l.lineDiscount(),
                        l.lineTax(),
                        l.lineTotal()))
                .toList();
        String appliedCode = priced.appliedCoupon() == null ? null
                : priced.appliedCoupon().getCode();
        return new CartResponse(
                cart.getId(), cart.getStatus().name(), appliedCode, lines,
                priced.subtotal(), priced.discountTotal(), priced.taxTotal(),
                priced.shippingTotal(), priced.grandTotal());
    }
}
