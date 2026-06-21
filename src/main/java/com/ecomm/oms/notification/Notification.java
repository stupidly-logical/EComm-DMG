package com.ecomm.oms.notification;

import com.ecomm.oms.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A customer notification. Sending is mocked: the row is persisted with status {@code SENT}
 * to stand in for an email/SMS dispatch.
 */
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(nullable = false, length = 40)
    private String channel;

    @Column
    private String payload;

    @Column(nullable = false, length = 20)
    private String status;

    protected Notification() {
    }

    public Notification(Long customerId, String type, String channel, String payload, String status) {
        this.customerId = customerId;
        this.type = type;
        this.channel = channel;
        this.payload = payload;
        this.status = status;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getType() {
        return type;
    }

    public String getChannel() {
        return channel;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }
}
