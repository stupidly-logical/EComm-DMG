package com.ecomm.oms.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable, transport-friendly envelope for paginated results so controllers never leak
 * Spring Data's {@code Page} structure directly into the API contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    public <R> PageResponse<R> map(java.util.function.Function<? super T, ? extends R> mapper) {
        return new PageResponse<>(
                content.stream().map(mapper).map(r -> (R) r).toList(),
                page, size, totalElements, totalPages, last);
    }
}
