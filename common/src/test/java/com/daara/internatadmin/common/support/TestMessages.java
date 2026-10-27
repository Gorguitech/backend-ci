package com.daara.internatadmin.common.support;

import com.daara.internatadmin.common.audit.AuditMessaging;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Outils de verification partages par les tests d'integration des services. */
public final class TestMessages {

    private TestMessages() {
    }

    /**
     * Evenements d'audit publies par le service teste (lus dans la file, sans consommateur dans ces tests).
     * La publication etant asynchrone, l'ordre d'arrivee n'est pas garanti.
     */
    public static List<JsonNode> drainAudit(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) throws IOException {
        List<JsonNode> messages = new ArrayList<>();
        Message message;
        while ((message = rabbitTemplate.receive(AuditMessaging.QUEUE, 2000)) != null) {
            messages.add(objectMapper.readTree(message.getBody()));
        }
        return messages;
    }

    /** Noms des champs d'un objet JSON, tries, pour les comparer aux modeles TypeScript du frontend. */
    public static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
