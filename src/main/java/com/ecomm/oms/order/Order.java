package com.ecomm.oms.order;

import com.ecomm.oms.common.error.ConflictException;
import com.ecomm.oms.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A placed order with its immutable money breakdown and lifecycle status. The status may only
 * move along {@link OrderStatus}'s allowed graph via {@link #transitionTo}; there is no raw
 * status setter, so every state change is validated in one place.
 */
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(name = "discount_total", nullable = false)
    private BigDecimal discountTotal;

    @Column(name = "tax_total", nullable = false)
    private BigDecimal taxTotal;

    @Column(name = "shipping_total", nullable = false)
    private BigDecimal shippingTotal;

    @Column(name = "grand_total", nullable = false)
    private BigDecimal grandTotal;

    @Column(name = "coupon_code", length = 64)
    private String couponCode;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(Long customerId, BigDecimal subtotal, BigDecimal discountTotal,
                 BigDecimal taxTotal, BigDecimal shippingTotal, BigDecimal grandTotal,
                 String couponCode) {
        this.customerId = customerId;
        this.status = OrderStatus.PLACED;
        this.subtotal = subtotal;
        this.discountTotal = discountTotal;
        this.taxTotal = taxTotal;
        this.shippingTotal = shippingTotal;
        this.grandTotal = grandTotal;
        this.couponCode = couponCode;
        this.placedAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        items.add(item);
    }

    /** Move to {@code target}, rejecting illegal transitions with a 409. */
    public void transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException(
                    "Cannot move order from " + status + " to " + target, "ILLEGAL_TRANSITION");
        }
        this.status = target;
    }

    public boolean isOwnedBy(Long userId) {
        return customerId.equals(userId);
    }

    public Long getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountTotal() {
        return discountTotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public BigDecimal getShippingTotal() {
        return shippingTotal;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
