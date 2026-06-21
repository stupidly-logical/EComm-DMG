package com.ecomm.oms.returns;

import com.ecomm.oms.audit.AuditService;
import com.ecomm.oms.common.Money;
import com.ecomm.oms.common.error.BusinessRuleException;
import com.ecomm.oms.common.error.NotFoundException;
import com.ecomm.oms.inventory.InventoryReservation;
import com.ecomm.oms.inventory.InventoryReservationRepository;
import com.ecomm.oms.inventory.InventoryService;
import com.ecomm.oms.inventory.Warehouse;
import com.ecomm.oms.inventory.WarehouseRepository;
import com.ecomm.oms.notification.NotificationService;
import com.ecomm.oms.order.Order;
import com.ecomm.oms.order.OrderItem;
import com.ecomm.oms.order.OrderRepository;
import com.ecomm.oms.order.OrderStatus;
import com.ecomm.oms.payment.PaymentGateway;
import com.ecomm.oms.returns.dto.CreateReturnRequest;
import com.ecomm.oms.returns.dto.ReturnResponse;
import com.ecomm.oms.security.AuthPrincipal;
import com.ecomm.oms.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Returns and refunds. A customer requests a return for a delivered order; an admin approves
 * it, which restocks the returned units, issues a refund through the gateway, and moves both
 * the return (→ REFUNDED) and the order (DELIVERED → RETURNED) forward. Both lifecycles are
 * guarded by their state machines.
 */
@Service
public class ReturnService {

    private final ReturnRequestRepository returnRepository;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final WarehouseRepository warehouseRepository;
    private final InventoryReservationRepository reservationRepository;
    private final PaymentGateway paymentGateway;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public ReturnService(ReturnRequestRepository returnRepository, RefundRepository refundRepository,
                         OrderRepository orderRepository, InventoryService inventoryService,
                         WarehouseRepository warehouseRepository,
                         InventoryReservationRepository reservationRepository,
                         PaymentGateway paymentGateway, AuditService auditService,
                         NotificationService notificationService) {
        this.returnRepository = returnRepository;
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.warehouseRepository = warehouseRepository;
        this.reservationRepository = reservationRepository;
        this.paymentGateway = paymentGateway;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public ReturnResponse requestReturn(AuthPrincipal customer, Long orderId, CreateReturnRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> NotFoundException.of("Order", orderId));
        if (!order.isOwnedBy(customer.userId())) {
            throw NotFoundException.of("Order", orderId);
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessRuleException(
                    "Only delivered orders can be returned", "ORDER_NOT_DELIVERED");
        }

        ReturnRequest ret = new ReturnRequest(orderId, customer.userId(), request.reason());
        for (CreateReturnRequest.Line line : request.items()) {
            OrderItem orderItem = order.getItems().stream()
                    .filter(i -> i.getId().equals(line.orderItemId()))
                    .findFirst()
                    .orElseThrow(() -> NotFoundException.of("OrderItem", line.orderItemId()));
            if (line.quantity() > orderItem.getQuantity()) {
                throw new BusinessRuleException(
                        "Cannot return more than ordered for item " + orderItem.getId(),
                        "RETURN_QTY_EXCEEDS");
            }
            ret.addItem(new ReturnItem(ret, orderItem, line.quantity(), line.reason()));
        }
        returnRepository.save(ret);
        auditService.record("Return", ret.getId(), "RETURN_REQUESTED", customer.email(), null);
        return ReturnResponse.from(ret, null);
    }

    @Transactional
    public ReturnResponse approve(Long returnId, String actor) {
        ReturnRequest ret = loadReturn(returnId);
        ret.transitionTo(ReturnStatus.APPROVED);
        Order order = orderRepository.findById(ret.getOrderId())
                .orElseThrow(() -> NotFoundException.of("Order", ret.getOrderId()));

        BigDecimal refundAmount = BigDecimal.ZERO;
        for (ReturnItem item : ret.getItems()) {
            OrderItem orderItem = item.getOrderItem();
            restock(order.getId(), orderItem, item.getQuantity());
            refundAmount = refundAmount.add(refundPortion(orderItem, item.getQuantity()));
        }
        refundAmount = Money.scale(refundAmount);

        PaymentGateway.RefundResult result = paymentGateway.refund("refund-" + returnId, refundAmount);
        Refund refund = refundRepository.save(new Refund(returnId, order.getId(), refundAmount,
                result.succeeded() ? "COMPLETED" : "FAILED", result.gatewayRef()));

        ret.transitionTo(ReturnStatus.REFUNDED);
        order.transitionTo(OrderStatus.RETURNED);
        auditService.record("Return", returnId, "RETURN_REFUNDED", actor, "amount=" + refundAmount);
        notificationService.notifyOrderStatus(order.getId(), order.getCustomerId(), "RETURNED");
        return ReturnResponse.from(ret, refund);
    }

    @Transactional
    public ReturnResponse reject(Long returnId, String actor) {
        ReturnRequest ret = loadReturn(returnId);
        ret.transitionTo(ReturnStatus.REJECTED);
        auditService.record("Return", returnId, "RETURN_REJECTED", actor, null);
        return ReturnResponse.from(ret, null);
    }

    @Transactional(readOnly = true)
    public ReturnResponse get(Long returnId, AuthPrincipal principal) {
        ReturnRequest ret = loadReturn(returnId);
        boolean privileged = principal.role() == Role.ADMIN;
        if (!privileged && !ret.isOwnedBy(principal.userId())) {
            throw NotFoundException.of("Return", returnId);
        }
        Refund refund = refundRepository.findByReturnRequestId(returnId).orElse(null);
        return ReturnResponse.from(ret, refund);
    }

    private ReturnRequest loadReturn(Long returnId) {
        return returnRepository.findById(returnId)
                .orElseThrow(() -> NotFoundException.of("Return", returnId));
    }

    /** Refund the full line total for a complete return, or a proportional share otherwise. */
    private BigDecimal refundPortion(OrderItem orderItem, int returnQty) {
        if (returnQty == orderItem.getQuantity()) {
            return orderItem.getLineTotal();
        }
        BigDecimal perUnit = orderItem.getLineTotal()
                .divide(BigDecimal.valueOf(orderItem.getQuantity()), Money.SCALE, Money.ROUNDING);
        return Money.multiply(perUnit, returnQty);
    }

    /** Return units to the line's fulfilling warehouse (or, if it was split, the first used one). */
    private void restock(Long orderId, OrderItem orderItem, int quantity) {
        Warehouse warehouse = resolveWarehouse(orderId, orderItem);
        if (warehouse != null) {
            inventoryService.addStock(orderItem.getProduct(), warehouse, quantity);
        }
    }

    private Warehouse resolveWarehouse(Long orderId, OrderItem orderItem) {
        if (orderItem.getAllocatedWarehouseId() != null) {
            return warehouseRepository.findById(orderItem.getAllocatedWarehouseId()).orElse(null);
        }
        return reservationRepository.findByOrderId(orderId).stream()
                .filter(r -> r.getProduct().getId().equals(orderItem.getProduct().getId()))
                .map(InventoryReservation::getWarehouse)
                .findFirst()
                .orElse(null);
    }
}
