package com.ecomm.oms.returns;

import com.ecomm.oms.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** A refund issued against a return. Status mirrors the mock gateway outcome. */
@Entity
@Table(name = "refunds")
public class Refund extends BaseEntity {

    @Column(name = "return_request_id")
    private Long returnRequestId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "gateway_ref", length = 80)
    private String gatewayRef;

    protected Refund() {
    }

    public Refund(Long returnRequestId, Long orderId, BigDecimal amount, String status, String gatewayRef) {
        this.returnRequestId = returnRequestId;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.gatewayRef = gatewayRef;
    }

    public Long getReturnRequestId() {
        return returnRequestId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public String getGatewayRef() {
        return gatewayRef;
    }
}
