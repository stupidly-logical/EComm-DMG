package com.ecomm.oms.fulfillment;

import com.ecomm.oms.fulfillment.dto.FulfillmentStatusRequest;
import com.ecomm.oms.fulfillment.dto.ShipmentResponse;
import com.ecomm.oms.fulfillment.dto.TrackingUpdateRequest;
import com.ecomm.oms.order.OrderService;
import com.ecomm.oms.order.dto.OrderResponse;
import com.ecomm.oms.security.AuthPrincipal;
import com.ecomm.oms.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Warehouse-staff fulfillment operations plus order-scoped shipment viewing.
 */
@RestController
@RequestMapping("/api/orders/{orderId}")
@Tag(name = "Fulfillment", description = "Warehouse-staff fulfillment and shipment tracking")
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;
    private final OrderService orderService;

    public FulfillmentController(FulfillmentService fulfillmentService, OrderService orderService) {
        this.fulfillmentService = fulfillmentService;
        this.orderService = orderService;
    }

    @PostMapping("/fulfillment/status")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Advance an order's fulfillment status (PACKED → SHIPPED → DELIVERED)")
    public OrderResponse advance(@CurrentUser AuthPrincipal me,
                                 @PathVariable Long orderId,
                                 @Valid @RequestBody FulfillmentStatusRequest request) {
        return OrderResponse.from(
                fulfillmentService.advanceStatus(orderId, request.status(), me.email()));
    }

    @PutMapping("/shipments/{shipmentId}/tracking")
    @PreAuthorize("hasRole('WAREHOUSE_STAFF')")
    @Operation(summary = "Set carrier and tracking number on a shipment")
    public ShipmentResponse updateTracking(@CurrentUser AuthPrincipal me,
                                           @PathVariable Long orderId,
                                           @PathVariable Long shipmentId,
                                           @Valid @RequestBody TrackingUpdateRequest request) {
        return ShipmentResponse.from(fulfillmentService.updateTracking(
                orderId, shipmentId, request.carrier(), request.trackingNumber(), me.email()));
    }

    @GetMapping("/shipments")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'WAREHOUSE_STAFF')")
    @Operation(summary = "List shipments for an order (own order for customers)")
    public List<ShipmentResponse> shipments(@CurrentUser AuthPrincipal me, @PathVariable Long orderId) {
        orderService.loadVisible(orderId, me); // enforces ownership/visibility
        return fulfillmentService.shipmentsForOrder(orderId).stream()
                .map(ShipmentResponse::from)
                .toList();
    }
}
