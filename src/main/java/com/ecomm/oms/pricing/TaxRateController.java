package com.ecomm.oms.pricing;

import com.ecomm.oms.pricing.dto.TaxRateRequest;
import com.ecomm.oms.pricing.dto.TaxRateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tax-rates")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Tax Rates", description = "Tax-rate administration (admin only)")
public class TaxRateController {

    private final TaxRateService taxRateService;

    public TaxRateController(TaxRateService taxRateService) {
        this.taxRateService = taxRateService;
    }

    @GetMapping
    @Operation(summary = "List tax rates")
    public List<TaxRateResponse> list() {
        return taxRateService.list().stream().map(TaxRateResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a tax rate for a category/region")
    public TaxRateResponse create(@Valid @RequestBody TaxRateRequest request) {
        return TaxRateResponse.from(taxRateService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a tax rate's percent")
    public TaxRateResponse update(@PathVariable Long id, @Valid @RequestBody TaxRateRequest request) {
        return TaxRateResponse.from(taxRateService.update(id, request));
    }
}
