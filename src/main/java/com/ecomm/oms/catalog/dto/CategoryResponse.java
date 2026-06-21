package com.ecomm.oms.catalog.dto;

import com.ecomm.oms.catalog.Category;

public record CategoryResponse(Long id, String name, Long parentId, String parentName) {

    public static CategoryResponse from(Category category) {
        Category parent = category.getParent();
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getName());
    }
}
