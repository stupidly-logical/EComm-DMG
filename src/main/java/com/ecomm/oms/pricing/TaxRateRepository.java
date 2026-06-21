package com.ecomm.oms.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {

    Optional<TaxRate> findByTaxCategoryAndRegion(String taxCategory, String region);

    boolean existsByTaxCategoryAndRegion(String taxCategory, String region);
}
