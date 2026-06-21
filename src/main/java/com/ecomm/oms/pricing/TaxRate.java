package com.ecomm.oms.pricing;

import com.ecomm.oms.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Tax rate for a product tax-category within a region, e.g. STANDARD/US → 8.250%. */
@Entity
@Table(name = "tax_rates")
public class TaxRate extends BaseEntity {

    @Column(name = "tax_category", nullable = false, length = 32)
    private String taxCategory;

    @Column(nullable = false, length = 64)
    private String region;

    @Column(name = "rate_percent", nullable = false)
    private BigDecimal ratePercent;

    protected TaxRate() {
    }

    public TaxRate(String taxCategory, String region, BigDecimal ratePercent) {
        this.taxCategory = taxCategory;
        this.region = region;
        this.ratePercent = ratePercent;
    }

    public String getTaxCategory() {
        return taxCategory;
    }

    public String getRegion() {
        return region;
    }

    public BigDecimal getRatePercent() {
        return ratePercent;
    }

    public void setRatePercent(BigDecimal ratePercent) {
        this.ratePercent = ratePercent;
    }
}
