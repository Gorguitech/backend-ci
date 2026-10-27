package com.daara.internatadmin.audit.messaging;

import com.daara.internatadmin.audit.AbstractIntegrationTest;
import com.daara.internatadmin.audit.domain.AuditLog;
import com.daara.internatadmin.audit.domain.AuditLogRepository;
import com.daara.internatadmin.common.audit.AuditEvent;
import com.daara.internatadmin.common.audit.AuditMessaging;
import com.daara.internatadmin.common.audit.RabbitAuditPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Chaine complete : publication par le producteur de common, consommation, persistance, DLQ. */
class AuditMessagingIT extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    AmqpAdmin amqpAdmin;

    @Autowired
    AuditLogRepository repository;

    private RabbitAuditPublisher publisher;

    @BeforeEach
    void reset() {
        repository.deleteAll();
        amqpAdmin.purgeQueue(AuditMessaging.DEAD_LETTER_QUEUE, false);
        publisher = new RabbitAuditPublisher(rabbitTemplate, Runnable::run);
        clearInvocations(auditService);
    }

    @Test
    void evenementPublieParUnService_enregistreAvecToutesSesDonnees() {
        LocalDateTime actionDate = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        AuditEvent event = new AuditEvent(UUID.randomUUID(), 5L, "awa.diop", "CREATE", "Enfant", 12L,
                "EnfantResponse[id=12, matricule=ENF-2026-0001]", actionDate);

        publisher.publish(event);

        await().atMost(TIMEOUT).until(() -> repository.count() == 1);
        AuditLog saved = repository.findAll().getFirst();
        assertThat(saved.getEventId()).isEqualTo(event.eventId());
        assertThat(saved.getUserId()).isEqualTo(5L);
        assertThat(saved.getUsername()).isEqualTo("awa.diop");
        assertThat(saved.getAction()).isEqualTo("CREATE");
        assertThat(saved.getEntityType()).isEqualTo("Enfant");
        assertThat(saved.getEntityId()).isEqualTo(12L);
        assertThat(saved.getDetails()).isEqualTo("EnfantResponse[id=12, matricule=ENF-2026-0001]");
        assertThat(saved.getActionDate()).isEqualTo(actionDate);
    }

    @Test
    void evenementRedistribue_uneSeuleTrace() {
        AuditEvent event = event("CREATE");
        publisher.publish(event);
        publisher.publish(event);
        AuditEvent marker = event("UPDATE");
        publisher.publish(marker);

        // file traitee dans l'ordre par un seul consommateur : le marqueur arrive apres le doublon
        await().atMost(TIMEOUT).until(() -> repository.findAll().stream()
                .anyMatch(log -> log.getEventId().equals(marker.eventId())));
        assertThat(repository.count()).isEqualTo(2);
        assertThat(rabbitTemplate.receive(AuditMessaging.DEAD_LETTER_QUEUE, 500)).isNull();
    }

    @Test
    void evenementIncomplet_envoyeEnDlqApresLesTentatives() {
        AuditEvent invalid = new AuditEvent(UUID.randomUUID(), null, "admin", "CREATE", "Enfant", 1L, "x",
                LocalDateTime.now());

        publisher.publish(invalid);

        Message dead = rabbitTemplate.receive(AuditMessaging.DEAD_LETTER_QUEUE, TIMEOUT.toMillis());
        assertThat(dead).as("message conserve dans la DLQ").isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).contains(invalid.eventId().toString());
        assertThat(repository.count()).isZero();
        verify(auditService, times(3)).record(argThat(e -> e != null && invalid.eventId().equals(e.eventId())));
    }

    @Test
    void messageIllisible_envoyeEnDlq() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message garbage = MessageBuilder.withBody("{pas du json".getBytes(StandardCharsets.UTF_8))
                .andProperties(properties).build();

        rabbitTemplate.send(AuditMessaging.EXCHANGE, AuditMessaging.routingKey("Enfant"), garbage);

        Message dead = rabbitTemplate.receive(AuditMessaging.DEAD_LETTER_QUEUE, TIMEOUT.toMillis());
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("{pas du json");
        assertThat(repository.count()).isZero();
    }

    @Test
    void detailTropLong_tronqueA500Caracteres() {
        AuditEvent event = new AuditEvent(UUID.randomUUID(), 1L, "admin", "UPDATE", "Parametres", 1L,
                "x".repeat(800), LocalDateTime.now());

        publisher.publish(event);

        await().atMost(TIMEOUT).until(() -> repository.count() == 1);
        assertThat(repository.findAll().getFirst().getDetails()).hasSize(500);
    }

    private static AuditEvent event(String action) {
        return new AuditEvent(UUID.randomUUID(), 1L, "admin", action, "Enfant", 3L, "d", LocalDateTime.now());
    }
}
