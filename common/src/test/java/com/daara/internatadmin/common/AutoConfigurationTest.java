package com.daara.internatadmin.common;

import com.daara.internatadmin.common.audit.AuditAspect;
import com.daara.internatadmin.common.audit.AuditAutoConfiguration;
import com.daara.internatadmin.common.audit.AuditPublisher;
import com.daara.internatadmin.common.audit.RabbitAuditPublisher;
import com.daara.internatadmin.common.feign.FeignRelayInterceptor;
import com.daara.internatadmin.common.feign.FeignSupportAutoConfiguration;
import com.daara.internatadmin.common.security.ResourceServerSecurityAutoConfiguration;
import com.daara.internatadmin.common.support.TestJwts;
import com.daara.internatadmin.common.tx.TransactionalRetry;
import com.daara.internatadmin.common.tx.TransactionalRetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/** Activation conditionnelle des briques du module selon la configuration du service. */
class AutoConfigurationTest {

    private final ApplicationContextRunner auditRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, RabbitAutoConfiguration.class,
                    AuditAutoConfiguration.class));

    @Test
    void audit_publicationActiveParDefaut() {
        auditRunner.run(context -> {
            assertThat(context).hasSingleBean(RabbitAuditPublisher.class);
            assertThat(context).hasSingleBean(AuditAspect.class);
            assertThat(context.getBean(TopicExchange.class).getName()).isEqualTo("audit.exchange");
        });
    }

    @Test
    void audit_publicationDesactiveePourAuditService() {
        auditRunner.withPropertyValues("internatadmin.audit.publisher.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(AuditPublisher.class);
            assertThat(context).doesNotHaveBean(AuditAspect.class);
            assertThat(context).hasBean("auditQueue").hasBean("auditDeadLetterQueue");
        });
    }

    @Test
    void audit_fileDurableAvecLettresMortesVersLaDlq() {
        auditRunner.run(context -> {
            org.springframework.amqp.core.Queue queue = context.getBean("auditQueue", org.springframework.amqp.core.Queue.class);
            assertThat(queue.getName()).isEqualTo("audit.events");
            assertThat(queue.isDurable()).isTrue();
            assertThat(queue.getArguments())
                    .containsEntry("x-dead-letter-exchange", "audit.dlx")
                    .containsEntry("x-dead-letter-routing-key", "audit.events.dlq");
            org.springframework.amqp.core.Queue dlq = context.getBean("auditDeadLetterQueue", org.springframework.amqp.core.Queue.class);
            assertThat(dlq.isDurable()).isTrue();
            assertThat(dlq.getArguments()).doesNotContainKey("x-message-ttl");
        });
    }

    @Test
    void securite_desactivableParPropriete() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ResourceServerSecurityAutoConfiguration.class))
                .withPropertyValues("internatadmin.security.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SecurityFilterChain.class));
    }

    @Test
    void securite_demarrageImpossibleSansCleInterne() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                        ResourceServerSecurityAutoConfiguration.class))
                .withPropertyValues("internatadmin.security.jwt-public-key-b64=" + TestJwts.publicKeyB64())
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void securite_demarrageImpossibleSiLaVariableDEnvironnementNEstPasDefinie() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                        ResourceServerSecurityAutoConfiguration.class))
                .withPropertyValues("internatadmin.security.jwt-public-key-b64=" + TestJwts.publicKeyB64(),
                        "internatadmin.security.internal-api-key=${INTERNAL_API_KEY}")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("variable d'environnement non definie"));
    }

    @Test
    void retry_absentSansGestionnaireDeTransactions() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TransactionalRetryAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(TransactionalRetry.class));
    }

    @Test
    void feign_intercepteurEnregistre() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                        FeignSupportAutoConfiguration.class))
                .withPropertyValues("internatadmin.security.internal-api-key=cle")
                .run(context -> assertThat(context).hasSingleBean(FeignRelayInterceptor.class));
    }
}
