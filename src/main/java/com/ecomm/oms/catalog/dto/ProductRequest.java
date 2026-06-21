package com.ecomm.oms.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal basePrice,
        @Size(max = 32) String taxCategory,
        Boolean active,
        Long categoryId) {

    /** Tax category defaults to STANDARD; active defaults to true when omitted. */
    public String taxCategoryOrDefault() {
        return (taxCategory == null || taxCategory.isBlank()) ? "STANDARD" : taxCategory;
    }

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
