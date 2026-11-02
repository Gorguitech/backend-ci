package com.daara.internatadmin.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String username, String action,
                       String entityType, Long entityId, String details) {
        AuditLog log = new AuditLog(userId, username, action, entityType, entityId, details);
        auditLogRepository.save(log);
    }


    public Page<AuditLog> search(String entityType, Long entityId, Long userId, Pageable pageable) {
        if (entityType != null && entityId != null) {
            return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
        }
        if (entityType != null) {
            return auditLogRepository.findByEntityType(entityType, pageable);
        }
        if (userId != null) {
            return auditLogRepository.findByUserId(userId, pageable);
        }
        return auditLogRepository.findAll(pageable);
    }
}