package com.ecomm.oms.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    boolean existsByCategoryId(Long categoryId);

    /** Initialise {@code category} so response mapping can read it outside the transaction. */
    @Override
    @EntityGraph(attributePaths = "category")
    Optional<Product> findById(Long id);

    /**
     * Active-product browse with optional category and free-text (name/SKU) filters.
     * Null filter arguments are ignored, so the same query backs the full catalog listing.
     */
    @EntityGraph(attributePaths = "category")
    @Query("""
            SELECT p FROM Product p
            WHERE p.active = true
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:q IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.sku)  LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Product> browse(@Param("categoryId") Long categoryId,
                         @Param("q") String q,
                         Pageable pageable);
}
