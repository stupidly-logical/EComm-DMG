package com.ecomm.oms.pricing.dto;

import com.ecomm.oms.pricing.CouponType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponRequest(
        @NotBlank @Size(max = 64) String code,
        @NotNull CouponType type,
        @NotNull @DecimalMin("0.00") BigDecimal value,
        @PositiveOrZero BigDecimal minOrderAmount,
        Instant validFrom,
        Instant validTo,
        @Positive Integer maxRedemptions,
        Boolean active) {

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
