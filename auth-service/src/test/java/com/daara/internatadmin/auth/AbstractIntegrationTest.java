package com.daara.internatadmin.auth;

import com.daara.internatadmin.common.audit.AuditMessaging;
import com.daara.internatadmin.common.support.TestJwts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contexte complet sur PostgreSQL et RabbitMQ reels, serveur sur un port aleatoire (necessaire pour
 * verifier la limite multipart appliquee par Tomcat). Avant chaque test, seul le compte admin initial
 * subsiste, dans son etat de premier demarrage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    protected static final String ADMIN_INITIAL_PASSWORD = "Admin-Initial-2026";
    protected static final String INTERNAL_KEY = "cle-interne-de-test";
    private static final String ADMIN_HASH = new BCryptPasswordEncoder().encode(ADMIN_INITIAL_PASSWORD);
    protected static final Path LOGO_DIR = createLogoDir();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    @Autowired
    protected AmqpAdmin amqpAdmin;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("internatadmin.security.jwt-public-key-b64", TestJwts::publicKeyB64);
        registry.add("internatadmin.jwt.private-key-b64", TestJwts::privateKeyB64);
        registry.add("internatadmin.security.internal-api-key", () -> INTERNAL_KEY);
        registry.add("internatadmin.admin.initial-password-hash", () -> ADMIN_HASH);
        registry.add("internatadmin.parametres.logo-dir", LOGO_DIR::toString);
    }

    private static Path createLogoDir() {
        try {
            return Files.createTempDirectory("logos");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void resetState() {
        jdbc.update("DELETE FROM users WHERE username <> 'admin'");
        jdbc.update("UPDATE users SET password_hash = ?, must_change_password = TRUE, recovery_code_hash = NULL, "
                + "active = TRUE, first_name = 'Admin', last_name = 'System' WHERE username = 'admin'", ADMIN_HASH);
        jdbc.update("UPDATE parametres_etablissement SET name = 'Daara (à configurer)', address = NULL, phone = NULL, "
                + "email = NULL, director_name = NULL, logo_path = NULL WHERE id = 1");
        amqpAdmin.purgeQueue(AuditMessaging.QUEUE, false);
    }

    protected Long adminId() {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = 'admin'", Long.class);
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected JsonNode login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginBody(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    protected String bearer(JsonNode loginResponse) {
        return "Bearer " + loginResponse.get("token").asText();
    }

    /** Jeton admin authentique, emis par le vrai login. */
    protected String adminBearer() throws Exception {
        return bearer(login("admin", ADMIN_INITIAL_PASSWORD));
    }

    /** Messages d'audit publies par le service depuis le dernier test (lus dans la file, sans consommateur). */
    protected List<JsonNode> drainAuditMessages() throws Exception {
        List<JsonNode> messages = new ArrayList<>();
        Message message;
        while ((message = rabbitTemplate.receive(AuditMessaging.QUEUE, 2000)) != null) {
            messages.add(objectMapper.readTree(message.getBody()));
        }
        return messages;
    }

    public record LoginBody(String username, String password) {
    }
}
