package com.daara.internatadmin.audit.domain;

import com.daara.internatadmin.common.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    static final int SHORT_TEXT_MAX = 50;
    static final int DETAILS_MAX = 500;

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Enregistre un evenement recu de RabbitMQ. Un evenement deja enregistre (redistribution) est ignore.
     * Un evenement invalide leve une exception : apres les tentatives configurees, il part en DLQ.
     */
    @Transactional
    public void record(AuditEvent event) {
        validate(event);

        if (repository.existsByEventId(event.eventId())) {
            log.info("Evenement d'audit deja enregistre, ignore : eventId={}", event.eventId());
            return;
        }

        repository.save(new AuditLog(
                event.eventId(),
                event.userId(),
                truncate(event.username(), SHORT_TEXT_MAX),
                truncate(event.action(), SHORT_TEXT_MAX),
                truncate(event.entityType(), SHORT_TEXT_MAX),
                event.entityId(),
                truncate(event.details(), DETAILS_MAX),
                event.actionDate() != null ? event.actionDate() : LocalDateTime.now(clock)));
    }

    /** Meme ordre de priorite des filtres que le monolithe. */
    @Transactional(readOnly = true)
    public Page<AuditLog> search(String entityType, Long entityId, Long userId, Pageable pageable) {
        if (entityType != null && entityId != null) {
            return repository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
        }
        if (entityType != null) {
            return repository.findByEntityType(entityType, pageable);
        }
        if (userId != null) {
            return repository.findByUserId(userId, pageable);
        }
        return repository.findAll(pageable);
    }

    private static void validate(AuditEvent event) {
        if (event == null || event.eventId() == null || event.userId() == null
                || isBlank(event.username()) || isBlank(event.action()) || isBlank(event.entityType())) {
            throw new IllegalArgumentException("Evenement d'audit incomplet : " + describe(event));
        }
    }

    // Identification sans le detail, qui ne doit pas apparaitre dans les journaux.
    private static String describe(AuditEvent event) {
        if (event == null) {
            return "null";
        }
        return "eventId=" + event.eventId() + ", action=" + event.action() + ", entityType=" + event.entityType();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int max) {
        return (value != null && value.length() > max) ? value.substring(0, max) : value;
    }
}
