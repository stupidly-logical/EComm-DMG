package com.ecomm.oms.pricing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Pricing configuration. {@code region} selects which {@link TaxRate} rows apply to this
 * store (single-region assumption); products with no matching rate are treated as tax-free.
 */
@ConfigurationProperties(prefix = "oms.pricing")
public record PricingProperties(String region) {

    public PricingProperties {
        if (region == null || region.isBlank()) {
            region = "US";
        }
    }
}
