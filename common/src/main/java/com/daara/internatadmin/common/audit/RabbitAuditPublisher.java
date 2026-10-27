package com.daara.internatadmin.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.Executor;

/**
 * Publication asynchrone, pour qu'un broker lent ou indisponible ne retarde jamais la reponse HTTP.
 * Un echec est journalise en ERROR (identifiant et action seulement, jamais le detail).
 * Les confirmations de publication doivent etre activees (spring.rabbitmq.publisher-confirm-type=correlated).
 */
public class RabbitAuditPublisher implements AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitAuditPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final Executor executor;

    public RabbitAuditPublisher(RabbitTemplate rabbitTemplate, Executor executor) {
        this.rabbitTemplate = rabbitTemplate;
        this.executor = executor;
    }

    @Override
    public void publish(AuditEvent event) {
        executor.execute(() -> send(event));
    }

    private void send(AuditEvent event) {
        CorrelationData correlation = new CorrelationData(event.eventId().toString());
        correlation.getFuture().whenComplete((confirm, error) -> {
            if (error != null || confirm == null || !confirm.isAck()) {
                logFailure(event, error != null ? error.toString() : "refus du broker");
            }
        });
        try {
            rabbitTemplate.convertAndSend(AuditMessaging.EXCHANGE,
                    AuditMessaging.routingKey(event.entityType()), event, correlation);
        } catch (AmqpException e) {
            logFailure(event, e.toString());
        }
    }

    private static void logFailure(AuditEvent event, String reason) {
        log.error("Evenement d'audit non publie : eventId={}, action={}, entityType={}, entityId={}, cause={}",
                event.eventId(), event.action(), event.entityType(), event.entityId(), reason);
    }
}
