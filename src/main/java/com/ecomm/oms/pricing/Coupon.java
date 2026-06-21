package com.ecomm.oms.pricing;

import com.ecomm.oms.common.BaseEntity;
import com.ecomm.oms.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A discount coupon. Validity (active flag, date window, redemption cap) and the discount
 * amount are domain behaviour on the entity so the cart, apply-coupon, and checkout paths all
 * agree on the rules.
 */
@Entity
@Table(name = "coupons")
public class Coupon extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CouponType type;

    @Column(name = "discount_value", nullable = false)
    private BigDecimal value;

    @Column(name = "min_order_amount", nullable = false)
    private BigDecimal minOrderAmount;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "times_redeemed", nullable = false)
    private int timesRedeemed;

    @Column(nullable = false)
    private boolean active;

    protected Coupon() {
    }

    public Coupon(String code, CouponType type, BigDecimal value, BigDecimal minOrderAmount,
                  Instant validFrom, Instant validTo, Integer maxRedemptions, boolean active) {
        this.code = code;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount == null ? Money.ZERO : minOrderAmount;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.maxRedemptions = maxRedemptions;
        this.timesRedeemed = 0;
        this.active = active;
    }

    /** Active, within its date window, and not over its redemption cap. */
    public boolean isRedeemable(Instant now) {
        if (!active) {
            return false;
        }
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        if (validTo != null && now.isAfter(validTo)) {
            return false;
        }
        return maxRedemptions == null || timesRedeemed < maxRedemptions;
    }

    public boolean meetsMinimum(BigDecimal subtotal) {
        return subtotal.compareTo(minOrderAmount) >= 0;
    }

    /**
     * The discount this coupon yields on the given subtotal, clamped so it never exceeds the
     * subtotal. Assumes the coupon has already been validated as redeemable for the order.
     */
    public BigDecimal discountFor(BigDecimal subtotal) {
        BigDecimal discount = switch (type) {
            case PERCENT -> Money.percentageOf(subtotal, value);
            case FIXED -> value;
        };
        BigDecimal scaled = Money.scale(discount);
        return scaled.compareTo(subtotal) > 0 ? subtotal : scaled;
    }

    public void redeem() {
        if (maxRedemptions != null && timesRedeemed >= maxRedemptions) {
            throw new IllegalStateException("Coupon redemption cap reached");
        }
        this.timesRedeemed++;
    }

    public String getCode() {
        return code;
    }

    public CouponType getType() {
        return type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public BigDecimal getMinOrderAmount() {
        return minOrderAmount;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public Integer getMaxRedemptions() {
        return maxRedemptions;
    }

    public int getTimesRedeemed() {
        return timesRedeemed;
    }

    public boolean isActive() {
        return active;
    }
}
