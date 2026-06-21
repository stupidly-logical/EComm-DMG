package com.ecomm.oms.order;

import com.ecomm.oms.audit.AuditService;
import com.ecomm.oms.common.error.NotFoundException;
import com.ecomm.oms.inventory.InventoryReservationRepository;
import com.ecomm.oms.inventory.InventoryService;
import com.ecomm.oms.order.dto.OrderResponse;
import com.ecomm.oms.security.AuthPrincipal;
import com.ecomm.oms.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read access to orders with ownership enforcement: a customer sees only their own orders;
 * an admin (or warehouse staff, for fulfillment) may see any. Non-owners are given a 404
 * rather than a 403 so order existence is not leaked.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    public OrderService(OrderRepository orderRepository,
                        InventoryReservationRepository reservationRepository,
                        InventoryService inventoryService,
                        AuditService auditService) {
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listForCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByIdDesc(customerId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getForPrincipal(Long orderId, AuthPrincipal principal) {
        Order order = loadVisible(orderId, principal);
        return OrderResponse.from(order);
    }

    /**
     * Cancel an order the principal owns (or any, for admins) and return its stock to
     * availability. Only legal from PLACED/CONFIRMED — {@link Order#transitionTo} rejects a
     * cancel once the order has shipped with a 409.
     */
    @Transactional
    public OrderResponse cancel(Long orderId, AuthPrincipal principal) {
        Order order = loadVisible(orderId, principal);
        order.transitionTo(OrderStatus.CANCELLED);
        inventoryService.restock(reservationRepository.findByOrderId(orderId));
        auditService.record("Order", orderId, "ORDER_CANCELLED", principal.email(), null);
        return OrderResponse.from(order);
    }

    /** Load an order the principal is allowed to see, or 404. */
    @Transactional(readOnly = true)
    public Order loadVisible(Long orderId, AuthPrincipal principal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> NotFoundException.of("Order", orderId));
        boolean privileged = principal.role() == Role.ADMIN || principal.role() == Role.WAREHOUSE_STAFF;
        if (!privileged && !order.isOwnedBy(principal.userId())) {
            throw NotFoundException.of("Order", orderId);
        }
        return order;
    }
}
