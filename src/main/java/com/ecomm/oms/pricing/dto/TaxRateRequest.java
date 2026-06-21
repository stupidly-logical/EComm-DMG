package com.ecomm.oms.pricing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TaxRateRequest(
        @NotBlank @Size(max = 32) String taxCategory,
        @NotBlank @Size(max = 64) String region,
        @NotNull @DecimalMin("0.000") @Digits(integer = 3, fraction = 3) BigDecimal ratePercent) {
}
