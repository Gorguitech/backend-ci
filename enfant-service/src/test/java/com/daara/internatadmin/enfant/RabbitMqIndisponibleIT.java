package com.daara.internatadmin.enfant;

import com.daara.internatadmin.common.support.TestJwts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sans broker joignable : l'action metier reussit et reste en base, l'evenement d'audit est perdu
 * avec une ligne ERROR dans le journal. Limite documentee dans le README.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.host=127.0.0.1",
        "spring.rabbitmq.port=1",
        "spring.rabbitmq.username=test",
        "spring.rabbitmq.password=test"
})
@AutoConfigureMockMvc
@Import(RabbitMqIndisponibleIT.PostgresSeul.class)
@ExtendWith(OutputCaptureExtension.class)
class RabbitMqIndisponibleIT {

    private static final String GESTIONNAIRE = "Bearer " + TestJwts.token(5, "awa.diop", "GESTIONNAIRE");
    private static final Duration DELAI_JOURNAL = Duration.ofSeconds(20);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("internatadmin.security.jwt-public-key-b64", TestJwts::publicKeyB64);
        registry.add("internatadmin.security.internal-api-key", () -> "cle-interne-de-test");
    }

    // create publie hors transaction, updateStatus apres le commit : les deux chemins de AuditAspect
    @Test
    void brokerInjoignable_actionsReussiesEtPerteDeLAuditJournalisee(CapturedOutput output) throws Exception {
        JsonNode created = objectMapper.readTree(mvc.perform(post("/api/enfants").param("confirmDuplicate", "false")
                        .header("Authorization", GESTIONNAIRE).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enfant())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long id = created.get("id").asLong();

        mvc.perform(patch("/api/enfants/" + id + "/status")
                        .header("Authorization", GESTIONNAIRE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SORTI\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT status FROM enfants WHERE id = ?", String.class, id))
                .as("action validee malgre la panne").isEqualTo("SORTI");

        await().atMost(DELAI_JOURNAL).untilAsserted(() -> assertThat(output.getAll())
                .contains("ERROR")
                .contains("Evenement d'audit non publie")
                .contains("action=CREATE, entityType=Enfant, entityId=" + id)
                .contains("action=UPDATE_STATUS, entityType=Enfant, entityId=" + id));
    }

    private static Map<String, Object> enfant() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Moussa");
        body.put("lastName", "Ndiaye");
        body.put("birthDate", "2018-03-02");
        body.put("gender", "M");
        body.put("guardianName", "Tuteur de Moussa");
        body.put("guardianPhone", "771234567");
        body.put("neighborhood", "Medina");
        body.put("entryDate", "2026-09-01");
        body.put("roomGroup", "Groupe A");
        body.put("registrationFee", 15000);
        body.put("monthlyFee", 10000);
        return body;
    }

    /** PostgreSQL seul : volontairement aucun conteneur RabbitMQ. */
    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresSeul {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
        }
    }
}
