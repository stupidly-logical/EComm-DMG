package com.ecomm.oms.pricing.dto;

import com.ecomm.oms.pricing.TaxRate;

import java.math.BigDecimal;

public record TaxRateResponse(Long id, String taxCategory, String region, BigDecimal ratePercent) {

    public static TaxRateResponse from(TaxRate rate) {
        return new TaxRateResponse(rate.getId(), rate.getTaxCategory(), rate.getRegion(),
                rate.getRatePercent());
    }
}
