package com.ecomm.oms.inventory.dto;

import com.ecomm.oms.inventory.Warehouse;

public record WarehouseResponse(Long id, String code, String name, String region, int priority) {

    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getRegion(),
                warehouse.getPriority());
    }
}
