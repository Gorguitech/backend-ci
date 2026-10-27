package com.daara.internatadmin.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Active l'audit dans les services qui declarent spring-boot-starter-amqp et spring-boot-starter-aop.
 * audit-service, seul consommateur, desactive la partie publication (internatadmin.audit.publisher.enabled=false).
 *
 * La topologie complete (exchange, file durable, DLX, DLQ) est declaree par les producteurs comme par le consommateur :
 * un evenement publie pendant un arret d'audit-service est conserve dans la file au lieu d'etre perdu.
 */
@AutoConfiguration(before = RabbitAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    MessageConverter auditMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    TopicExchange auditExchange() {
        return new TopicExchange(AuditMessaging.EXCHANGE, true, false);
    }

    @Bean
    DirectExchange auditDeadLetterExchange() {
        return new DirectExchange(AuditMessaging.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue auditQueue() {
        return QueueBuilder.durable(AuditMessaging.QUEUE)
                .deadLetterExchange(AuditMessaging.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(AuditMessaging.DEAD_LETTER_QUEUE)
                .build();
    }

    // Sans TTL ni limite : les messages non traites restent consultables et rejouables.
    @Bean
    Queue auditDeadLetterQueue() {
        return QueueBuilder.durable(AuditMessaging.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding auditBinding(Queue auditQueue, TopicExchange auditExchange) {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with(AuditMessaging.ROUTING_KEY_PATTERN);
    }

    @Bean
    Binding auditDeadLetterBinding(Queue auditDeadLetterQueue, DirectExchange auditDeadLetterExchange) {
        return BindingBuilder.bind(auditDeadLetterQueue).to(auditDeadLetterExchange).with(AuditMessaging.DEAD_LETTER_QUEUE);
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock auditClock() {
        return Clock.systemDefaultZone();
    }

    /** Publication et interception des methodes @Audited : uniquement dans les services producteurs. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Aspect.class)
    @ConditionalOnProperty(prefix = "internatadmin.audit.publisher", name = "enabled", matchIfMissing = true)
    static class PublisherConfiguration {

        @Bean
        @ConditionalOnMissingBean(AuditPublisher.class)
        AuditPublisher rabbitAuditPublisher(RabbitTemplate rabbitTemplate) {
            Executor executor = Executors.newVirtualThreadPerTaskExecutor();
            return new RabbitAuditPublisher(rabbitTemplate, executor);
        }

        @Bean
        AuditAspect auditAspect(AuditPublisher publisher, Clock clock) {
            return new AuditAspect(publisher, clock);
        }
    }
}
