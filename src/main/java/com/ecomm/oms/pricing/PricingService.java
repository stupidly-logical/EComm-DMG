package com.ecomm.oms.pricing;

import com.ecomm.oms.cart.Cart;
import com.ecomm.oms.cart.CartItem;
import com.ecomm.oms.common.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a cart into a fully-priced order breakdown: line subtotals from price snapshots,
 * an optional coupon discount allocated across lines, per-line tax by the product's tax
 * category, and the reconciled totals. The arithmetic is delegated to the pure
 * {@link PricingCalculator}; this service only resolves rates/coupons from the database.
 *
 * <p>For a cart <em>preview</em> an attached-but-now-invalid coupon (expired, below minimum,
 * cap reached) is silently ignored rather than failing the request; the strict check lives in
 * {@link CouponService#validateForOrder} and runs at apply-coupon and checkout time.
 */
@Service
public class PricingService {

    private static final BigDecimal SHIPPING = Money.ZERO; // free shipping (documented assumption)

    private final TaxRateService taxRateService;
    private final CouponRepository couponRepository;

    public PricingService(TaxRateService taxRateService, CouponRepository couponRepository) {
        this.taxRateService = taxRateService;
        this.couponRepository = couponRepository;
    }

    @Transactional(readOnly = true)
    public PricedCart price(Cart cart) {
        List<CartItem> items = cart.getItems();

        BigDecimal subtotal = items.stream()
                .map(item -> Money.multiply(item.getUnitPriceSnapshot(), item.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        subtotal = Money.scale(subtotal);

        Coupon applied = resolveCouponForPreview(cart.getCouponCode(), subtotal);
        BigDecimal discount = applied == null ? Money.ZERO : applied.discountFor(subtotal);

        List<PricingCalculator.Line> calcLines = items.stream()
                .map(item -> new PricingCalculator.Line(
                        item.getUnitPriceSnapshot(),
                        item.getQuantity(),
                        taxRateService.rateFor(item.getProduct().getTaxCategory())))
                .toList();

        PricingCalculator.Result result = PricingCalculator.price(calcLines, discount);

        List<PricedCart.Line> lines = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            CartItem item = items.get(i);
            PricingCalculator.PricedLine pl = result.lines().get(i);
            lines.add(new PricedCart.Line(
                    item.getProduct(), item.getQuantity(), pl.unitPrice(),
                    pl.lineSubtotal(), pl.lineDiscount(), pl.lineTax(), pl.lineTotal()));
        }

        BigDecimal grandTotal = Money.scale(result.grandTotal().add(SHIPPING));
        return new PricedCart(lines, result.subtotal(), result.discountTotal(),
                result.taxTotal(), SHIPPING, grandTotal, applied);
    }

    private Coupon resolveCouponForPreview(String code, BigDecimal subtotal) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return couponRepository.findByCode(code.trim())
                .filter(c -> c.isRedeemable(Instant.now()) && c.meetsMinimum(subtotal))
                .orElse(null);
    }
}
