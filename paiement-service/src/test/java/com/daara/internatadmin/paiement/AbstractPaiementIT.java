package com.daara.internatadmin.paiement;

import com.daara.internatadmin.common.audit.AuditMessaging;
import com.daara.internatadmin.common.support.InternatAdminContainers;
import com.daara.internatadmin.common.support.TestJwts;
import com.daara.internatadmin.common.support.TestMessages;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * PostgreSQL et RabbitMQ reels ; enfant-service et personnel-service simules par WireMock, avec des reponses au
 * format exact de leurs endpoints internes. Delai du verrou de mensualite reduit a 2 s pour les tests.
 */
@SpringBootTest(properties = "internatadmin.paiement.lock-timeout=2s")
@AutoConfigureMockMvc
@Import(InternatAdminContainers.class)
abstract class AbstractPaiementIT {

    static final String INTERNAL_KEY = "cle-interne-de-test";
    static final String TOKEN = TestJwts.token(5, "awa.diop", "GESTIONNAIRE");
    static final String GESTIONNAIRE = "Bearer " + TOKEN;

    static final WireMockServer ENFANT_SERVICE = start();
    static final WireMockServer PERSONNEL_SERVICE = start();

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    AmqpAdmin amqpAdmin;

    @Autowired
    Resilience4JCircuitBreakerFactory circuitBreakerFactory;

    private static WireMockServer start() {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        server.start();
        return server;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("internatadmin.security.jwt-public-key-b64", TestJwts::publicKeyB64);
        registry.add("internatadmin.security.internal-api-key", () -> INTERNAL_KEY);
        registry.add("internatadmin.services.enfant", ENFANT_SERVICE::baseUrl);
        registry.add("internatadmin.services.personnel", PERSONNEL_SERVICE::baseUrl);
    }

    @BeforeEach
    void resetState() {
        jdbc.update("DELETE FROM paiements_enfants");
        jdbc.update("DELETE FROM paiements_salaires");
        amqpAdmin.purgeQueue(AuditMessaging.QUEUE, false);
        ENFANT_SERVICE.resetAll();
        PERSONNEL_SERVICE.resetAll();
        circuitBreakerFactory.getCircuitBreakerRegistry().getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    // ---------------------------------------------------------------- services simules

    /** Fiche eleve au format de GET /internal/enfants/{id} d'enfant-service. */
    static Map<String, Object> enfant(long id, String firstName, String lastName, int registrationFee, int monthlyFee) {
        return Map.ofEntries(
                Map.entry("id", id), Map.entry("matricule", "ENF-2026-" + String.format("%04d", id)),
                Map.entry("firstName", firstName), Map.entry("lastName", lastName),
                Map.entry("fullName", firstName + " " + lastName), Map.entry("birthDate", "2018-03-02"),
                Map.entry("gender", "M"), Map.entry("guardianName", "Tuteur de " + firstName),
                Map.entry("guardianPhone", "77000000" + id), Map.entry("entryDate", "2026-01-05"),
                Map.entry("status", "ACTIF"), Map.entry("registrationFee", money(registrationFee)),
                Map.entry("monthlyFee", money(monthlyFee)));
    }

    /** Fiche employe au format de GET /internal/employes/{id} de personnel-service. */
    static Map<String, Object> employe(long id, String firstName, String lastName, int baseSalary) {
        return Map.ofEntries(
                Map.entry("id", id), Map.entry("matricule", "EMP-2026-" + String.format("%04d", id)),
                Map.entry("firstName", firstName), Map.entry("lastName", lastName),
                Map.entry("fullName", firstName + " " + lastName), Map.entry("phone", "77000000" + id),
                Map.entry("function", "MAITRE_CORANIQUE"), Map.entry("contractType", "TEMPS_PLEIN"),
                Map.entry("baseSalary", money(baseSalary)), Map.entry("hireDate", "2026-01-05"), Map.entry("status", "ACTIF"));
    }

    /** Montant au format NUMERIC(10,2) renvoye par les services (15000.00). */
    static java.math.BigDecimal money(int value) {
        return new java.math.BigDecimal(value).setScale(2);
    }

    void stubEnfant(Map<String, Object> enfant) throws Exception {
        ENFANT_SERVICE.stubFor(get("/internal/enfants/" + enfant.get("id")).willReturn(json(enfant)));
    }

    void stubEnfantsSuivis(List<Map<String, Object>> enfants) throws Exception {
        ENFANT_SERVICE.stubFor(get(urlPathEqualTo("/internal/enfants")).willReturn(json(enfants)));
    }

    void stubEmploye(Map<String, Object> employe) throws Exception {
        PERSONNEL_SERVICE.stubFor(get("/internal/employes/" + employe.get("id")).willReturn(json(employe)));
    }

    void stubEmployesSuivis(List<Map<String, Object>> employes) throws Exception {
        PERSONNEL_SERVICE.stubFor(get(urlPathEqualTo("/internal/employes")).willReturn(json(employes)));
    }

    private com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(Object body) throws Exception {
        return aResponse().withHeader("Content-Type", "application/json").withBody(objectMapper.writeValueAsString(body));
    }

    // ---------------------------------------------------------------- appels

    ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).header("Authorization", GESTIONNAIRE)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    static Map<String, Object> mensualite(long enfantId, int amount, boolean confirmPartial) {
        return Map.of("enfantId", enfantId, "paymentType", "MENSUALITE", "paymentMethod", "ESPECES", "amount", amount,
                "concernedYear", 2026, "concernedMonth", 10, "paymentDate", "2026-10-05", "confirmPartial", confirmPartial);
    }

    static Map<String, Object> inscription(long enfantId, int amount) {
        return Map.of("enfantId", enfantId, "paymentType", "INSCRIPTION", "paymentMethod", "WAVE", "amount", amount,
                "paymentDate", "2026-09-28");
    }

    JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    List<JsonNode> drainAudit() throws Exception {
        return TestMessages.drainAudit(rabbitTemplate, objectMapper);
    }
}
