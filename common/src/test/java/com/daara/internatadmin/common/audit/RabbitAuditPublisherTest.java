package com.daara.internatadmin.common.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.ConnectException;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class RabbitAuditPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitAuditPublisher publisher = new RabbitAuditPublisher(rabbitTemplate, Runnable::run);

    private final AuditEvent event = new AuditEvent(UUID.randomUUID(), 1L, "admin", "CREATE", "Enfant", 12L,
            "EnfantResponse[detail-confidentiel]", LocalDateTime.now());

    @Test
    void publieSurLExchangeAuditAvecUneCleDeRoutageParType() {
        publisher.publish(event);

        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(eq("audit.exchange"), eq("audit.Enfant"), eq(event), correlation.capture());
        assertThat(correlation.getValue().getId()).isEqualTo(event.eventId().toString());
    }

    @Test
    void brokerIndisponible_aucuneExceptionEtJournalSansLeDetail(CapturedOutput output) {
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();

        assertThat(output.getAll())
                .contains("Evenement d'audit non publie")
                .contains(event.eventId().toString())
                .doesNotContain("detail-confidentiel");
    }

    @Test
    void refusDuBroker_journaliseEnErreur(CapturedOutput output) {
        publisher.publish(event);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), correlation.capture());

        correlation.getValue().getFuture().complete(new CorrelationData.Confirm(false, "nack"));

        assertThat(output.getAll()).contains("Evenement d'audit non publie").contains("refus du broker");
    }

    @Test
    void confirmationPositive_aucuneErreur(CapturedOutput output) {
        publisher.publish(event);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), correlation.capture());

        correlation.getValue().getFuture().complete(new CorrelationData.Confirm(true, null));

        assertThat(output.getAll()).doesNotContain("Evenement d'audit non publie");
    }
}
