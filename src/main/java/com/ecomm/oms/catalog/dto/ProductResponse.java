package com.ecomm.oms.catalog.dto;

import com.ecomm.oms.catalog.Category;
import com.ecomm.oms.catalog.Product;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal basePrice,
        String taxCategory,
        boolean active,
        Long categoryId,
        String categoryName) {

    public static ProductResponse from(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getBasePrice(),
                product.getTaxCategory(),
                product.isActive(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName());
    }
}
