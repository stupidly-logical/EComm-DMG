package com.ecomm.oms.fulfillment;

import com.ecomm.oms.audit.AuditService;
import com.ecomm.oms.common.error.BusinessRuleException;
import com.ecomm.oms.common.error.NotFoundException;
import com.ecomm.oms.inventory.InventoryReservation;
import com.ecomm.oms.inventory.InventoryReservationRepository;
import com.ecomm.oms.order.Order;
import com.ecomm.oms.order.OrderRepository;
import com.ecomm.oms.order.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fulfillment: routing a freshly placed order into shipments (post-commit), and the
 * warehouse-staff-driven progression CONFIRMED → PACKED → SHIPPED → DELIVERED. Order status
 * legality is enforced by {@link Order#transitionTo}; this service maps those moves onto
 * shipment state and audits each one.
 */
@Service
public class FulfillmentService {

    private static final Set<OrderStatus> STAFF_TARGETS =
            Set.of(OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final InventoryReservationRepository reservationRepository;
    private final AuditService auditService;

    public FulfillmentService(OrderRepository orderRepository, ShipmentRepository shipmentRepository,
                              InventoryReservationRepository reservationRepository,
                              AuditService auditService) {
        this.orderRepository = orderRepository;
        this.shipmentRepository = shipmentRepository;
        this.reservationRepository = reservationRepository;
        this.auditService = auditService;
    }

    /**
     * Create one shipment per fulfilling warehouse and advance PLACED → CONFIRMED. Idempotent:
     * a re-delivered event for an order no longer PLACED is ignored.
     */
    @Transactional
    public void routeNewOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> NotFoundException.of("Order", orderId));
        if (order.getStatus() != OrderStatus.PLACED) {
            return;
        }
        Set<Long> warehouseIds = new LinkedHashSet<>();
        for (InventoryReservation reservation : reservationRepository.findByOrderId(orderId)) {
            warehouseIds.add(reservation.getWarehouse().getId());
        }
        for (Long warehouseId : warehouseIds) {
            shipmentRepository.save(new Shipment(orderId, warehouseId));
        }
        order.transitionTo(OrderStatus.CONFIRMED);
        auditService.record("Order", orderId, "ORDER_CONFIRMED", "SYSTEM",
                "Routed into " + warehouseIds.size() + " shipment(s)");
    }

    /** Warehouse staff drive the order forward; reflect SHIPPED/DELIVERED onto shipments. */
    @Transactional
    public Order advanceStatus(Long orderId, OrderStatus target, String actor) {
        if (!STAFF_TARGETS.contains(target)) {
            throw new BusinessRuleException(
                    "Warehouse staff may only set PACKED, SHIPPED, or DELIVERED", "INVALID_FULFILLMENT_TARGET");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> NotFoundException.of("Order", orderId));
        order.transitionTo(target);

        if (target == OrderStatus.SHIPPED) {
            updateShipmentStatuses(orderId, ShipmentStatus.SHIPPED);
        } else if (target == OrderStatus.DELIVERED) {
            updateShipmentStatuses(orderId, ShipmentStatus.DELIVERED);
        }
        auditService.record("Order", orderId, "ORDER_" + target.name(), actor, null);
        return order;
    }

    @Transactional
    public Shipment updateTracking(Long orderId, Long shipmentId, String carrier,
                                   String trackingNumber, String actor) {
        Shipment shipment = shipmentRepository.findByIdAndOrderId(shipmentId, orderId)
                .orElseThrow(() -> NotFoundException.of("Shipment", shipmentId));
        shipment.updateTracking(carrier, trackingNumber);
        auditService.record("Shipment", shipmentId, "TRACKING_UPDATED", actor, carrier + " " + trackingNumber);
        return shipment;
    }

    @Transactional(readOnly = true)
    public List<Shipment> shipmentsForOrder(Long orderId) {
        return shipmentRepository.findByOrderId(orderId);
    }

    private void updateShipmentStatuses(Long orderId, ShipmentStatus status) {
        shipmentRepository.findByOrderId(orderId).forEach(s -> s.setStatus(status));
    }
}
