package com.daara.internatadmin.common.audit;

/** Topologie RabbitMQ de l'audit, partagee par les producteurs et par audit-service. */
public final class AuditMessaging {

    public static final String EXCHANGE = "audit.exchange";
    public static final String QUEUE = "audit.events";
    public static final String DEAD_LETTER_EXCHANGE = "audit.dlx";
    public static final String DEAD_LETTER_QUEUE = "audit.events.dlq";
    public static final String ROUTING_KEY_PREFIX = "audit.";
    public static final String ROUTING_KEY_PATTERN = "audit.#";

    private AuditMessaging() {
    }

    public static String routingKey(String entityType) {
        return ROUTING_KEY_PREFIX + entityType;
    }
}
