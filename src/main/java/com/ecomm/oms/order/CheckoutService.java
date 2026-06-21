package com.ecomm.oms.order;

import com.ecomm.oms.cart.Cart;
import com.ecomm.oms.cart.CartItem;
import com.ecomm.oms.cart.CartService;
import com.ecomm.oms.cart.CartStatus;
import com.ecomm.oms.common.error.BusinessRuleException;
import com.ecomm.oms.inventory.InventoryReservation;
import com.ecomm.oms.inventory.InventoryService;
import com.ecomm.oms.order.dto.CheckoutRequest;
import com.ecomm.oms.order.dto.OrderResponse;
import com.ecomm.oms.payment.PaymentRepository;
import com.ecomm.oms.payment.PaymentService;
import com.ecomm.oms.pricing.PricedCart;
import com.ecomm.oms.pricing.PricingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Places an order from the customer's active cart as a single atomic transaction:
 *
 * <ol>
 *   <li>load + validate the cart (non-empty, all products still active);</li>
 *   <li>price it (line prices, coupon, taxes → totals);</li>
 *   <li>reserve inventory under pessimistic locks (allocating warehouses, may split);</li>
 *   <li>charge payment (idempotent; a decline throws and rolls everything back);</li>
 *   <li>on success: persist the order PLACED, confirm reservations (decrement stock),
 *       redeem the coupon, mark the cart CHECKED_OUT.</li>
 * </ol>
 *
 * Because steps 1–5 share one {@code @Transactional} boundary, cart, inventory, and payment
 * state move together — there is no window in which stock is decremented for an unpaid order.
 */
@Service
public class CheckoutService {

    private final CartService cartService;
    private final PricingService pricingService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public CheckoutService(CartService cartService, PricingService pricingService,
                           InventoryService inventoryService, PaymentService paymentService,
                           OrderRepository orderRepository, PaymentRepository paymentRepository) {
        this.cartService = cartService;
        this.pricingService = pricingService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public OrderResponse placeOrder(Long customerId, CheckoutRequest request) {
        // Idempotent replay: a repeated checkout with the same key returns the original order.
        String idempotencyKey = resolveIdempotencyKey(request.idempotencyKey());
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            var replay = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (replay.isPresent()) {
                return OrderResponse.from(replay.get().getOrder());
            }
        }

        Cart cart = cartService.getActiveCart(customerId);
        if (cart.isEmpty()) {
            throw new BusinessRuleException("Cannot checkout an empty cart", "CART_EMPTY");
        }
        for (CartItem item : cart.getItems()) {
            if (!item.getProduct().isActive()) {
                throw new BusinessRuleException(
                        "Product " + item.getProduct().getSku() + " is no longer available",
                        "PRODUCT_INACTIVE");
            }
        }

        PricedCart priced = pricingService.price(cart);
        if (cart.getCouponCode() != null && priced.appliedCoupon() == null) {
            throw new BusinessRuleException(
                    "The applied coupon is no longer valid", "COUPON_INVALID");
        }

        Order order = new Order(customerId, priced.subtotal(), priced.discountTotal(),
                priced.taxTotal(), priced.shippingTotal(), priced.grandTotal(),
                priced.appliedCoupon() == null ? null : priced.appliedCoupon().getCode());

        // Reserve stock for every line first (holds units; may split across warehouses).
        List<InventoryReservation> reservations = new ArrayList<>();
        for (PricedCart.Line line : priced.lines()) {
            List<InventoryReservation> lineReservations =
                    inventoryService.reserve(line.product(), line.quantity());
            Long allocatedWarehouseId = lineReservations.size() == 1
                    ? lineReservations.get(0).getWarehouse().getId() : null;
            order.addItem(new OrderItem(order, line.product(), line.quantity(), line.unitPrice(),
                    line.lineDiscount(), line.lineTax(), line.lineTotal(), allocatedWarehouseId));
            reservations.addAll(lineReservations);
        }

        orderRepository.save(order);
        reservations.forEach(r -> r.assignToOrder(order.getId()));

        // Charge after reserving; a decline throws here and the whole transaction rolls back.
        paymentService.charge(order, idempotencyKey, request.paymentToken(), request.paymentMethod());

        // Payment succeeded: confirm holds (decrement on-hand), redeem coupon, close the cart.
        inventoryService.confirm(reservations);
        if (priced.appliedCoupon() != null) {
            priced.appliedCoupon().redeem();
        }
        cart.setStatus(CartStatus.CHECKED_OUT);

        return OrderResponse.from(order);
    }

    private String resolveIdempotencyKey(String provided) {
        return (provided == null || provided.isBlank()) ? UUID.randomUUID().toString() : provided.trim();
    }
}
