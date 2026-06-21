package com.ecomm.oms.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {

    Optional<StockLevel> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    List<StockLevel> findByProductId(Long productId);

    /**
     * Lock every stock row for a product across warehouses, ordered by allocation priority,
     * emitting {@code SELECT … FOR UPDATE}. Two concurrent checkouts contending for the same
     * product serialize on these row locks, which is what prevents oversell. Rows are always
     * locked in the same (priority, id) order to avoid deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM StockLevel s
            JOIN s.warehouse w
            WHERE s.product.id = :productId
            ORDER BY w.priority ASC, w.id ASC
            """)
    List<StockLevel> lockByProductForUpdate(@Param("productId") Long productId);
}
