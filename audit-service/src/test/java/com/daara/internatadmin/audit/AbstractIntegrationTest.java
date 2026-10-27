package com.daara.internatadmin.audit;

import com.daara.internatadmin.audit.domain.AuditService;
import com.daara.internatadmin.common.support.InternatAdminContainers;
import com.daara.internatadmin.common.support.TestJwts;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Contexte complet sur PostgreSQL et RabbitMQ reels. Les delais entre tentatives sont raccourcis
 * pour les tests ; leur nombre (3) reste celui de la configuration de production.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=200ms"
})
@AutoConfigureMockMvc
@Import(InternatAdminContainers.class)
public abstract class AbstractIntegrationTest {

    // Espion declare ici pour que toutes les classes partagent un seul contexte (et une seule paire de conteneurs).
    @MockitoSpyBean
    protected AuditService auditService;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("internatadmin.security.jwt-public-key-b64", TestJwts::publicKeyB64);
        registry.add("internatadmin.security.internal-api-key", () -> "cle-interne-de-test");
    }
}
