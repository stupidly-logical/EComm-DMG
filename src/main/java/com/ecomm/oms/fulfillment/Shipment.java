package com.ecomm.oms.fulfillment;

import com.ecomm.oms.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A shipment for an order from a single warehouse. An order split across warehouses has one
 * shipment per warehouse. Carrier/tracking are filled in by warehouse staff.
 */
@Entity
@Table(name = "shipments")
public class Shipment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(length = 80)
    private String carrier;

    @Column(name = "tracking_number", length = 120)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status;

    protected Shipment() {
    }

    public Shipment(Long orderId, Long warehouseId) {
        this.orderId = orderId;
        this.warehouseId = warehouseId;
        this.status = ShipmentStatus.PENDING;
    }

    public void updateTracking(String carrier, String trackingNumber) {
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
    }

    public void setStatus(ShipmentStatus status) {
        this.status = status;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public ShipmentStatus getStatus() {
        return status;
    }
}
