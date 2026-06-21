package com.ecomm.oms.inventory.dto;

import com.ecomm.oms.inventory.StockLevel;

public record StockLevelResponse(
        Long productId,
        String productSku,
        Long warehouseId,
        String warehouseCode,
        int quantityOnHand,
        int quantityReserved,
        int available) {

    public static StockLevelResponse from(StockLevel stock) {
        return new StockLevelResponse(
                stock.getProduct().getId(),
                stock.getProduct().getSku(),
                stock.getWarehouse().getId(),
                stock.getWarehouse().getCode(),
                stock.getQuantityOnHand(),
                stock.getQuantityReserved(),
                stock.getAvailable());
    }
}
