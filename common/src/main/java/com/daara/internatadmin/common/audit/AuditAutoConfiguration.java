package com.daara.internatadmin.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Active l'audit dans les services qui declarent spring-boot-starter-amqp et spring-boot-starter-aop.
 * audit-service, seul consommateur, desactive la partie publication (internatadmin.audit.publisher.enabled=false).
 */
@AutoConfiguration(before = RabbitAutoConfiguration.class)
@ConditionalOnClass({RabbitTemplate.class, Aspect.class})
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    MessageConverter auditMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "internatadmin.audit.publisher", name = "enabled", matchIfMissing = true)
    TopicExchange auditExchange() {
        return new TopicExchange(AuditMessaging.EXCHANGE, true, false);
    }

    @Bean
    @ConditionalOnMissingBean(AuditPublisher.class)
    @ConditionalOnProperty(prefix = "internatadmin.audit.publisher", name = "enabled", matchIfMissing = true)
    AuditPublisher rabbitAuditPublisher(RabbitTemplate rabbitTemplate) {
        Executor executor = Executors.newVirtualThreadPerTaskExecutor();
        return new RabbitAuditPublisher(rabbitTemplate, executor);
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock auditClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    @ConditionalOnProperty(prefix = "internatadmin.audit.publisher", name = "enabled", matchIfMissing = true)
    AuditAspect auditAspect(AuditPublisher publisher, Clock clock) {
        return new AuditAspect(publisher, clock);
    }
}
