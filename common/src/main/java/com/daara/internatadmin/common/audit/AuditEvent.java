package com.daara.internatadmin.common.audit;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Message publie sur RabbitMQ et consomme par audit-service.
 * eventId rend la consommation idempotente en cas de redistribution du message.
 */
public record AuditEvent(
        UUID eventId,
        Long userId,
        String username,
        String action,
        String entityType,
        Long entityId,
        String details,
        LocalDateTime actionDate
) {
}
