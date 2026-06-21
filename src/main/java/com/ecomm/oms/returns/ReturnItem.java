package com.ecomm.oms.returns;

import com.ecomm.oms.common.BaseEntity;
import com.ecomm.oms.order.OrderItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A single returned line, referencing the original order line and the quantity returned. */
@Entity
@Table(name = "return_items")
public class ReturnItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequest returnRequest;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 255)
    private String reason;

    protected ReturnItem() {
    }

    public ReturnItem(ReturnRequest returnRequest, OrderItem orderItem, int quantity, String reason) {
        this.returnRequest = returnRequest;
        this.orderItem = orderItem;
        this.quantity = quantity;
        this.reason = reason;
    }

    public ReturnRequest getReturnRequest() {
        return returnRequest;
    }

    public OrderItem getOrderItem() {
        return orderItem;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getReason() {
        return reason;
    }
}
