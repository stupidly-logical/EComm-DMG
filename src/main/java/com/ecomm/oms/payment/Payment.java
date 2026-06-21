package com.ecomm.oms.payment;

import com.ecomm.oms.common.BaseEntity;
import com.ecomm.oms.order.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A payment attempt against an order. {@code idempotencyKey} is unique so a retried checkout
 * with the same key cannot double-charge.
 */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 40)
    private String method;

    @Column(name = "gateway_ref", length = 80)
    private String gatewayRef;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    protected Payment() {
    }

    public Payment(Order order, BigDecimal amount, PaymentStatus status, String method,
                   String gatewayRef, String idempotencyKey) {
        this.order = order;
        this.amount = amount;
        this.status = status;
        this.method = method;
        this.gatewayRef = gatewayRef;
        this.idempotencyKey = idempotencyKey;
    }

    public Order getOrder() {
        return order;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getMethod() {
        return method;
    }

    public String getGatewayRef() {
        return gatewayRef;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
