package com.ecomm.oms.inventory;

import com.ecomm.oms.catalog.Product;
import com.ecomm.oms.catalog.ProductRepository;
import com.ecomm.oms.common.error.ConflictException;
import com.ecomm.oms.common.error.NotFoundException;
import com.ecomm.oms.inventory.dto.StockAdjustmentRequest;
import com.ecomm.oms.inventory.dto.WarehouseRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Warehouse administration plus manual stock adjustments. The concurrency-safe reservation
 * path lives in {@code InventoryService} (added with checkout); this service handles the
 * admin-driven setup of locations and on-hand quantities.
 */
@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final StockLevelRepository stockLevelRepository;
    private final ProductRepository productRepository;

    public WarehouseService(WarehouseRepository warehouseRepository,
                            StockLevelRepository stockLevelRepository,
                            ProductRepository productRepository) {
        this.warehouseRepository = warehouseRepository;
        this.stockLevelRepository = stockLevelRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<Warehouse> list() {
        return warehouseRepository.findAll(Sort.by("priority", "id"));
    }

    @Transactional(readOnly = true)
    public Warehouse get(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Warehouse", id));
    }

    @Transactional
    public Warehouse create(WarehouseRequest request) {
        if (warehouseRepository.existsByCode(request.code())) {
            throw new ConflictException("Warehouse code already exists", "WAREHOUSE_CODE_TAKEN");
        }
        return warehouseRepository.save(new Warehouse(
                request.code().trim(), request.name().trim(), request.region(), request.priority()));
    }

    @Transactional
    public Warehouse update(Long id, WarehouseRequest request) {
        Warehouse warehouse = get(id);
        if (!warehouse.getCode().equals(request.code())
                && warehouseRepository.existsByCode(request.code())) {
            throw new ConflictException("Warehouse code already exists", "WAREHOUSE_CODE_TAKEN");
        }
        warehouse.setCode(request.code().trim());
        warehouse.setName(request.name().trim());
        warehouse.setRegion(request.region());
        warehouse.setPriority(request.priority());
        return warehouse;
    }

    @Transactional
    public void delete(Long id) {
        warehouseRepository.delete(get(id));
    }

    /**
     * Set the absolute on-hand quantity for a product at this warehouse, creating the stock
     * row on first adjustment. The new quantity may not fall below units already reserved.
     */
    @Transactional
    public StockLevel adjustStock(Long warehouseId, StockAdjustmentRequest request) {
        Warehouse warehouse = get(warehouseId);
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> NotFoundException.of("Product", request.productId()));

        StockLevel stock = stockLevelRepository
                .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                .orElseGet(() -> new StockLevel(product, warehouse, 0));

        if (request.quantityOnHand() < stock.getQuantityReserved()) {
            throw new ConflictException(
                    "On-hand cannot be set below reserved (" + stock.getQuantityReserved() + ")",
                    "STOCK_BELOW_RESERVED");
        }
        stock.setQuantityOnHand(request.quantityOnHand());
        return stockLevelRepository.save(stock);
    }
}
