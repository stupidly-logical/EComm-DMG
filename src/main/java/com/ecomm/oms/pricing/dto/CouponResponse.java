package com.ecomm.oms.pricing.dto;

import com.ecomm.oms.pricing.Coupon;
import com.ecomm.oms.pricing.CouponType;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponResponse(
        Long id,
        String code,
        CouponType type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        Instant validFrom,
        Instant validTo,
        Integer maxRedemptions,
        int timesRedeemed,
        boolean active) {

    public static CouponResponse from(Coupon c) {
        return new CouponResponse(c.getId(), c.getCode(), c.getType(), c.getValue(),
                c.getMinOrderAmount(), c.getValidFrom(), c.getValidTo(),
                c.getMaxRedemptions(), c.getTimesRedeemed(), c.isActive());
    }
}
