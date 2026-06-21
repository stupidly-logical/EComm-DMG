package com.ecomm.oms.pricing;

import com.ecomm.oms.common.error.ConflictException;
import com.ecomm.oms.common.error.NotFoundException;
import com.ecomm.oms.pricing.dto.TaxRateRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TaxRateService {

    private final TaxRateRepository taxRateRepository;
    private final PricingProperties pricingProperties;

    public TaxRateService(TaxRateRepository taxRateRepository, PricingProperties pricingProperties) {
        this.taxRateRepository = taxRateRepository;
        this.pricingProperties = pricingProperties;
    }

    @Transactional(readOnly = true)
    public List<TaxRate> list() {
        return taxRateRepository.findAll(Sort.by("taxCategory", "region"));
    }

    @Transactional
    public TaxRate create(TaxRateRequest request) {
        if (taxRateRepository.existsByTaxCategoryAndRegion(request.taxCategory(), request.region())) {
            throw new ConflictException("Tax rate already exists for category/region",
                    "TAX_RATE_EXISTS");
        }
        return taxRateRepository.save(new TaxRate(
                request.taxCategory().trim(), request.region().trim(), request.ratePercent()));
    }

    @Transactional
    public TaxRate update(Long id, TaxRateRequest request) {
        TaxRate rate = taxRateRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("TaxRate", id));
        rate.setRatePercent(request.ratePercent());
        return rate;
    }

    /**
     * Rate percent for a product tax-category in the configured store region, or zero when no
     * matching rate is configured (treated as tax-free).
     */
    @Transactional(readOnly = true)
    public BigDecimal rateFor(String taxCategory) {
        return taxRateRepository
                .findByTaxCategoryAndRegion(taxCategory, pricingProperties.region())
                .map(TaxRate::getRatePercent)
                .orElse(BigDecimal.ZERO);
    }
}
