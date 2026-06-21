package com.ecomm.oms.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {

    Optional<StockLevel> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    List<StockLevel> findByProductId(Long productId);
}
