package com.ecomm.oms.catalog;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByParentId(Long parentId);

    /** Initialise {@code parent} for response mapping outside the transaction. */
    @Override
    @EntityGraph(attributePaths = "parent")
    Optional<Category> findById(Long id);

    @EntityGraph(attributePaths = "parent")
    List<Category> findAllByOrderByIdAsc();
}
