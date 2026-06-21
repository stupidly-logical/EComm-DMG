package com.ecomm.oms.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit records. Called both synchronously (within a business transaction) and from
 * the post-commit async pipeline; default propagation joins an active transaction or starts a
 * fresh one on the async thread.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(String entityType, Long entityId, String action, String actor, String details) {
        auditLogRepository.save(new AuditLog(entityType, entityId, action, actor, details));
    }
}
