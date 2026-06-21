package com.ecomm.oms.returns;

import com.ecomm.oms.common.BaseEntity;
import com.ecomm.oms.common.error.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * A customer's request to return part or all of a delivered order. Status moves only along
 * {@link ReturnStatus}'s graph via {@link #transitionTo}.
 */
@Entity
@Table(name = "return_requests")
public class ReturnRequest extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnStatus status;

    @Column(length = 500)
    private String reason;

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ReturnItem> items = new ArrayList<>();

    protected ReturnRequest() {
    }

    public ReturnRequest(Long orderId, Long customerId, String reason) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.reason = reason;
        this.status = ReturnStatus.REQUESTED;
    }

    public void addItem(ReturnItem item) {
        items.add(item);
    }

    public void transitionTo(ReturnStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException(
                    "Cannot move return from " + status + " to " + target, "ILLEGAL_TRANSITION");
        }
        this.status = target;
    }

    public boolean isOwnedBy(Long userId) {
        return customerId.equals(userId);
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public ReturnStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public List<ReturnItem> getItems() {
        return items;
    }
}
