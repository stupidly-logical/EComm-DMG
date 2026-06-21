package com.ecomm.oms.audit;

import com.ecomm.oms.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * An append-only record of a significant action. {@code createdAt} (from {@link BaseEntity})
 * is the event timestamp.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 120)
    private String actor;

    @Column
    private String details;

    protected AuditLog() {
    }

    public AuditLog(String entityType, Long entityId, String action, String actor, String details) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.actor = actor;
        this.details = details;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public String getActor() {
        return actor;
    }

    public String getDetails() {
        return details;
    }
}
