package com.daara.internatadmin.audit.messaging;

import com.daara.internatadmin.audit.domain.AuditService;
import com.daara.internatadmin.common.audit.AuditEvent;
import com.daara.internatadmin.common.audit.AuditMessaging;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consommateur de la file audit.events. En cas d'echec, les nouvelles tentatives et le rejet vers la DLQ
 * sont pris en charge par la configuration spring.rabbitmq.listener.simple.retry.
 */
@Component
public class AuditEventListener {

    private final AuditService auditService;

    public AuditEventListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @RabbitListener(queues = AuditMessaging.QUEUE)
    public void onEvent(AuditEvent event) {
        auditService.record(event);
    }
}
