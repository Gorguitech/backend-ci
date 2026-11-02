package com.daara.internatadmin.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Base64;
import java.util.UUID;

/**
 * Application complete sur un vrai PostgreSQL, appelee en HTTP comme par le frontend : le retour d'erreur
 * de Tomcat (403 a corps vide, 413) n'existe pas avec MockMvc. Tous les secrets sont generes a chaque execution.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractApiIT {

    protected static final JsonMapper JSON = JsonMapper.builder().build();
    protected static final String JWT_SECRET = Base64.getEncoder().encodeToString(
            (UUID.randomUUID().toString() + UUID.randomUUID()).getBytes());

    private static final String ADMIN_INITIAL_PASSWORD = "Initial-" + UUID.randomUUID();
    private static final String ADMIN_PASSWORD = "Admin-" + UUID.randomUUID();
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static String adminToken;

    static {
        POSTGRES.start();
    }

    @LocalServerPort
    protected int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> JWT_SECRET);
        registry.add("app.admin.initial-password-hash",
                () -> new BCryptPasswordEncoder(4).encode(ADMIN_INITIAL_PASSWORD));
        registry.add("app.storage.logo-dir", Files.createTempDirectory("logos")::toString);
        registry.add("app.paiement.lock-timeout", () -> "3s");
    }

    protected static String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    protected static String jdbcUsername() {
        return POSTGRES.getUsername();
    }

    protected static String jdbcPassword() {
        return POSTGRES.getPassword();
    }

    protected static String adminInitialPassword() {
        return ADMIN_INITIAL_PASSWORD;
    }

    /** Premier appel : connexion initiale et changement de mot de passe obligatoire. */
    protected synchronized String adminToken() throws Exception {
        if (adminToken == null) {
            String initial = login("admin", ADMIN_INITIAL_PASSWORD).get("token").asString();
            HttpResponse<String> changed = send("PUT", "/api/users/me/password", initial,
                    "{\"currentPassword\":\"" + ADMIN_INITIAL_PASSWORD + "\",\"newPassword\":\"" + ADMIN_PASSWORD + "\"}");
            if (changed.statusCode() != 200) {
                throw new IllegalStateException("Changement du mot de passe admin : " + changed.statusCode());
            }
            adminToken = login("admin", ADMIN_PASSWORD).get("token").asString();
        }
        return adminToken;
    }

    protected JsonNode login(String username, String password) throws Exception {
        HttpResponse<String> response = send("POST", "/api/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Connexion de " + username + " : " + response.statusCode());
        }
        return JSON.readTree(response.body());
    }

    protected HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = request(path, token)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpRequest.Builder request(String path, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    protected HttpClient client() {
        return CLIENT;
    }

    /** Eleve au nom unique, pour que les tests restent independants sur la base partagee. */
    protected JsonNode createEnfant(int entryYear, int monthlyFee) throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        HttpResponse<String> response = send("POST", "/api/enfants", adminToken(), """
                {"firstName":"Awa","lastName":"%s","birthDate":"2015-03-20","gender":"F","guardianName":"Fatou",
                 "guardianPhone":"%s","entryDate":"%d-01-15","registrationFee":5000,"monthlyFee":%d}
                """.formatted(unique, unique, entryYear, monthlyFee));
        if (response.statusCode() != 201) {
            throw new IllegalStateException("Creation d'un eleve : " + response.statusCode() + " " + response.body());
        }
        return JSON.readTree(response.body());
    }
}
