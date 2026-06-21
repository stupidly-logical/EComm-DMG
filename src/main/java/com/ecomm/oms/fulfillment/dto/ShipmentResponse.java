package com.ecomm.oms.fulfillment.dto;

import com.ecomm.oms.fulfillment.Shipment;

public record ShipmentResponse(
        Long id,
        Long orderId,
        Long warehouseId,
        String carrier,
        String trackingNumber,
        String status) {

    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getId(),
                shipment.getOrderId(),
                shipment.getWarehouseId(),
                shipment.getCarrier(),
                shipment.getTrackingNumber(),
                shipment.getStatus().name());
    }
}
