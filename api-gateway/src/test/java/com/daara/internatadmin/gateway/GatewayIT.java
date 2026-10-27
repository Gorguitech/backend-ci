package com.daara.internatadmin.gateway;

import com.daara.internatadmin.common.support.TestJwts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway sur un vrai port HTTP ; chaque service est simule par un serveur WireMock distinct,
 * ce qui permet de verifier quel service recoit chaque requete et ce qu'il recoit exactement.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIT {

    private static final String INTERNAL_KEY = "cle-interne-de-test";
    private static final String FRONTEND = "http://localhost:4200";
    private static final Map<String, WireMockServer> SERVICES = startServices(
            "auth", "enfant", "personnel", "paiement", "comptabilite", "pilotage", "audit");

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String token = TestJwts.token(1, "admin", "ADMIN");

    @LocalServerPort
    int port;

    @Autowired
    Resilience4JCircuitBreakerFactory circuitBreakerFactory;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        SERVICES.forEach((name, server) ->
                registry.add("internatadmin.gateway.services." + name, server::baseUrl));
        registry.add("internatadmin.security.jwt-public-key-b64", TestJwts::publicKeyB64);
        registry.add("internatadmin.security.internal-api-key", () -> INTERNAL_KEY);
        registry.add("internatadmin.gateway.cors-allowed-origins", () -> FRONTEND);
    }

    private static Map<String, WireMockServer> startServices(String... names) {
        Map<String, WireMockServer> servers = new LinkedHashMap<>();
        for (String name : names) {
            WireMockServer server = new WireMockServer(options().dynamicPort());
            server.start();
            warmUp(server);
            servers.put(name, server);
        }
        return servers;
    }

    // La premiere requete servie par WireMock est lente (demarrage de Jetty) : elle ne doit pas peser
    // sur le delai court de la gateway pendant le premier test.
    private static void warmUp(WireMockServer server) {
        try {
            HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(server.baseUrl() + "/__admin/health")).build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new IllegalStateException("WireMock indisponible", e);
        }
    }

    @AfterAll
    static void stopServices() {
        SERVICES.values().forEach(WireMockServer::stop);
    }

    @BeforeEach
    void reset() {
        SERVICES.forEach((name, server) -> {
            server.resetAll();
            // chaque service repond son nom : on sait ainsi qui a recu la requete
            server.stubFor(any(anyUrl()).atPriority(10).willReturn(aResponse().withStatus(200)
                    .withHeader("Content-Type", "text/plain").withBody(name)));
        });
        accountState(1, true, false);
        circuitBreakerFactory.getCircuitBreakerRegistry().find(AccountStateClient.BREAKER_ID).ifPresent(CircuitBreaker::reset);
    }

    // ---------------------------------------------------------------- routage

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "/api/enfants/stats, pilotage",
            "/api/employes/stats, pilotage",
            "/api/dashboard, pilotage",
            "/api/notifications, pilotage",
            "/api/enfants, enfant",
            "/api/enfants/12, enfant",
            "/api/enfants/search?q=Diop, enfant",
            "/api/enfants/matricule/ENF-2026-0001, enfant",
            "/api/enfants?status=ACTIF, enfant",
            "/api/employes, personnel",
            "/api/employes/3, personnel",
            "/api/employes/matricule/EMP-2026-0001, personnel",
            "/api/users, auth",
            "/api/users/stats, auth",
            "/api/parametres, auth",
            "/api/paiements-enfants/mensualites?year=2026&month=10&status=ALL, paiement",
            "/api/paiements-enfants/enfant/4/mensualites, paiement",
            "/api/paiements-salaires/employe/2, paiement",
            "/api/categories-depenses?status=ACTIVE, comptabilite",
            "/api/depenses?year=2026&month=10, comptabilite",
            "/api/autres-recettes/7/receipt-data, comptabilite",
            "/api/tresorerie?year=2026&month=10, comptabilite",
            "/api/audit/me?page=1, audit"
    })
    void routage_versLeBonServiceAvecCheminEtParametresIntacts(String path, String expectedService) throws Exception {
        HttpResponse<String> response = send("GET", path, Map.of("Authorization", "Bearer " + token), null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(expectedService);
        SERVICES.get(expectedService).verify(getRequestedFor(urlEqualTo(path)));
        SERVICES.forEach((name, server) -> {
            if (!name.equals(expectedService) && !name.equals("auth")) {
                server.verify(0, anyRequestedFor(anyUrl()));
            }
        });
    }

    @Test
    void routeStatsPrioritaireSurIdentifiant_enfantServiceNeLaRecoitPas() throws Exception {
        send("GET", "/api/enfants/stats", bearer(), null);
        SERVICES.get("enfant").verify(0, anyRequestedFor(anyUrl()));
        SERVICES.get("pilotage").verify(getRequestedFor(urlEqualTo("/api/enfants/stats")));
    }

    @Test
    void methodeEtCorpsRelayes() throws Exception {
        HttpResponse<String> response = send("PATCH", "/api/enfants/12/status",
                Map.of("Authorization", "Bearer " + token, "Content-Type", "application/json"), "{\"status\":\"SORTI\"}".getBytes());

        assertThat(response.statusCode()).isEqualTo(200);
        SERVICES.get("enfant").verify(patchRequestedFor(urlEqualTo("/api/enfants/12/status"))
                .withRequestBody(equalTo("{\"status\":\"SORTI\"}")));
    }

    @Test
    void cheminsInternes_jamaisRoutes() throws Exception {
        HttpResponse<String> response = send("GET", "/internal/enfants/1", bearer(), null);
        assertThat(response.statusCode()).isEqualTo(404);
        // seul l'appel de controle de l'etat du compte, emis par la gateway elle-meme, atteint un service
        SERVICES.forEach((name, server) -> server.verify(0, anyRequestedFor(urlPathEqualTo("/internal/enfants/1"))));
        SERVICES.get("auth").verify(1, anyRequestedFor(anyUrl()));
    }

    // ---------------------------------------------------------------- jeton

    @Test
    void cheminPublic_loginRelayeSansControleNiEtatDuCompte() throws Exception {
        SERVICES.get("auth").stubFor(post("/api/auth/login").willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"INVALID_CURRENT_PASSWORD\",\"message\":\"Identifiant ou mot de passe incorrect.\"}")));

        HttpResponse<String> response = send("POST", "/api/auth/login", Map.of("Content-Type", "application/json"),
                "{\"username\":\"admin\",\"password\":\"faux\"}".getBytes());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json(response).get("message").asText()).isEqualTo("Identifiant ou mot de passe incorrect.");
        SERVICES.get("auth").verify(0, getRequestedFor(urlPathEqualTo("/internal/users/1/state")));
    }

    @Test
    void sansJeton_403SansAppelerNiLeServiceNiAuthService() throws Exception {
        HttpResponse<String> response = send("GET", "/api/enfants", Map.of(), null);

        assertThat(response.statusCode()).isEqualTo(403);
        SERVICES.values().forEach(server -> server.verify(0, anyRequestedFor(anyUrl())));
    }

    @Test
    void jetonInvalide_401InvalidTokenSansAppelerAuthService() throws Exception {
        String expired = TestJwts.token(1, "admin", "ADMIN", Instant.now().minusSeconds(70));
        String otherKey = TestJwts.sign(TestJwts.OTHER_KEY,
                TestJwts.claims(1, "admin", "ADMIN", Instant.now().plusSeconds(600)).build());

        for (String invalid : List.of(expired, otherKey, "pas-un-jwt")) {
            HttpResponse<String> response = send("GET", "/api/enfants", Map.of("Authorization", "Bearer " + invalid), null);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(json(response).get("error").asText()).isEqualTo("INVALID_TOKEN");
            assertThat(json(response).get("message").asText()).isEqualTo("Jeton invalide ou expiré.");
        }
        SERVICES.values().forEach(server -> server.verify(0, anyRequestedFor(anyUrl())));
    }

    @Test
    void jetonExpireDansLaToleranceDe60s_accepte() throws Exception {
        String token = TestJwts.token(1, "admin", "ADMIN", Instant.now().minusSeconds(50));
        assertThat(send("GET", "/api/enfants", Map.of("Authorization", "Bearer " + token), null).statusCode()).isEqualTo(200);
    }

    @Test
    void requeteRelayee_jetonEtIdentifiantDeCorrelationTransmis() throws Exception {
        HttpResponse<String> response = send("GET", "/api/enfants",
                Map.of("Authorization", "Bearer " + token, "X-Request-Id", "demande-42"), null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("X-Request-Id")).containsExactly("demande-42");
        SERVICES.get("auth").verify(getRequestedFor(urlEqualTo("/internal/users/1/state"))
                .withHeader("Authorization", equalTo("Bearer " + token))
                .withHeader("X-Internal-Key", equalTo(INTERNAL_KEY))
                .withHeader("X-Request-Id", equalTo("demande-42")));
        SERVICES.get("enfant").verify(getRequestedFor(urlEqualTo("/api/enfants"))
                .withHeader("Authorization", equalTo("Bearer " + token))
                .withHeader("X-Request-Id", equalTo("demande-42")));
    }

    // ---------------------------------------------------------------- etat du compte

    @Test
    void compteDesactive_bloqueDesLaRequeteSuivante() throws Exception {
        assertThat(send("GET", "/api/enfants", bearer(), null).statusCode()).isEqualTo(200);

        accountState(1, false, false);
        HttpResponse<String> response = send("GET", "/api/enfants", bearer(), null);

        assertThat(response.statusCode()).isEqualTo(403);
        SERVICES.get("enfant").verify(1, anyRequestedFor(anyUrl()));
        SERVICES.get("auth").verify(2, getRequestedFor(urlEqualTo("/internal/users/1/state")));
    }

    @Test
    void utilisateurInconnuDAuthService_403() throws Exception {
        SERVICES.get("auth").stubFor(get("/internal/users/1/state").willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"INVALID_REQUEST\",\"message\":\"Utilisateur introuvable : 1\"}")));

        assertThat(send("GET", "/api/enfants", bearer(), null).statusCode()).isEqualTo(403);
        SERVICES.get("enfant").verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void changementDeMotDePasseObligatoire_403SaufSurLaRouteDeChangement() throws Exception {
        accountState(1, true, true);

        HttpResponse<String> blocked = send("GET", "/api/enfants", bearer(), null);
        assertThat(blocked.statusCode()).isEqualTo(403);
        assertThat(json(blocked).get("error").asText()).isEqualTo("MUST_CHANGE_PASSWORD");
        assertThat(json(blocked).get("message").asText()).isEqualTo("Changement de mot de passe obligatoire.");

        HttpResponse<String> allowed = send("PUT", "/api/users/me/password",
                Map.of("Authorization", "Bearer " + token, "Content-Type", "application/json"),
                "{\"currentPassword\":\"a\",\"newPassword\":\"b\"}".getBytes());
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.body()).isEqualTo("auth");
        SERVICES.get("enfant").verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void authServiceEnErreur_503EtJamais401() throws Exception {
        SERVICES.get("auth").stubFor(get("/internal/users/1/state").willReturn(aResponse().withStatus(500)));
        assertServiceUnavailable(send("GET", "/api/enfants", bearer(), null));

        SERVICES.get("auth").stubFor(get("/internal/users/1/state")
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertServiceUnavailable(send("GET", "/api/enfants", bearer(), null));
        SERVICES.get("enfant").verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void authServiceTropLent_503ApresLeDelaiCourt() throws Exception {
        SERVICES.get("auth").stubFor(get("/internal/users/1/state").willReturn(aResponse().withFixedDelay(5000)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":1,\"active\":true,\"mustChangePassword\":false,\"role\":\"ADMIN\"}")));

        long start = System.nanoTime();
        HttpResponse<String> response = send("GET", "/api/enfants", bearer(), null);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertServiceUnavailable(response);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
    }

    @Test
    void authServiceDurablementEnPanne_circuitOuvertEtReponseImmediateSansAppel() throws Exception {
        SERVICES.get("auth").stubFor(get("/internal/users/1/state").willReturn(aResponse().withStatus(500)));
        for (int i = 0; i < 10; i++) {
            assertServiceUnavailable(send("GET", "/api/enfants", bearer(), null));
        }
        SERVICES.get("auth").resetRequests();

        assertServiceUnavailable(send("GET", "/api/enfants", bearer(), null));
        SERVICES.get("auth").verify(0, anyRequestedFor(anyUrl()));
    }

    // ---------------------------------------------------------------- services en aval

    @Test
    void serviceEnAvalInjoignable_503() throws Exception {
        SERVICES.get("enfant").stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertServiceUnavailable(send("GET", "/api/enfants", bearer(), null));
    }

    @Test
    void erreurDuServiceEnAval_relayeeTelleQuelle() throws Exception {
        SERVICES.get("paiement").stubFor(post("/api/paiements-enfants").willReturn(aResponse().withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"PARTIAL_PAYMENT_NOT_CONFIRMED\",\"message\":\"Paiement partiel\"}")));
        HttpResponse<String> response = send("POST", "/api/paiements-enfants",
                Map.of("Authorization", "Bearer " + token, "Content-Type", "application/json"), "{}".getBytes());

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json(response).get("error").asText()).isEqualTo("PARTIAL_PAYMENT_NOT_CONFIRMED");
    }

    // ---------------------------------------------------------------- logo

    @Test
    void logo_multipartRelayeIntact() throws Exception {
        byte[] png = new byte[20_000];
        for (int i = 0; i < png.length; i++) {
            png[i] = (byte) i;
        }
        Multipart multipart = multipart(png);
        SERVICES.get("auth").stubFor(post("/api/parametres/logo").willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"hasLogo\":true}")));

        HttpResponse<String> response = send("POST", "/api/parametres/logo",
                Map.of("Authorization", "Bearer " + token, "Content-Type", multipart.contentType()), multipart.body());

        assertThat(response.statusCode()).isEqualTo(200);
        // Tomcat ajoute ";charset=UTF-8" au type de contenu, sans effet sur le decoupage des parties :
        // ce qui compte est que la limite (boundary) et le corps arrivent intacts.
        SERVICES.get("auth").verify(postRequestedFor(urlEqualTo("/api/parametres/logo"))
                .withHeader("Content-Type", containing("multipart/form-data"))
                .withHeader("Content-Type", containing("boundary=limite-InternatAdmin-42"))
                .withRequestBody(binaryEqualTo(multipart.body())));
    }

    @Test
    void logo_413FileTooLargeDAuthServiceRecuIntactParLeClient() throws Exception {
        Multipart multipart = multipart(new byte[3_500_000]);
        SERVICES.get("auth").stubFor(post("/api/parametres/logo").willReturn(aResponse().withStatus(413)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"FILE_TOO_LARGE\",\"message\":\"Le fichier dépasse la taille maximale autorisée.\"}")));

        HttpResponse<String> response = send("POST", "/api/parametres/logo",
                Map.of("Authorization", "Bearer " + token, "Content-Type", multipart.contentType()), multipart.body());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(json(response).get("error").asText()).isEqualTo("FILE_TOO_LARGE");
        assertThat(json(response).get("message").asText()).isEqualTo("Le fichier dépasse la taille maximale autorisée.");
    }

    @Test
    void logo_lectureBinaireAvecLeJeton() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, (byte) 0xFF};
        SERVICES.get("auth").stubFor(get("/api/parametres/logo").willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "image/png").withBody(png)));

        HttpResponse<byte[]> response = client.send(request("GET", "/api/parametres/logo", bearer(), null),
                HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(response.body()).isEqualTo(png);
    }

    // ---------------------------------------------------------------- CORS, sante, documentation

    @Test
    void cors_requetePreliminaireDuFrontendAutoriseeSansJeton() throws Exception {
        HttpResponse<String> response = send("OPTIONS", "/api/enfants", Map.of(
                "Origin", FRONTEND,
                "Access-Control-Request-Method", "PATCH",
                "Access-Control-Request-Headers", "authorization,content-type"), null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(FRONTEND);
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
        SERVICES.values().forEach(server -> server.verify(0, anyRequestedFor(anyUrl())));
    }

    @Test
    void cors_origineInconnueRefusee() throws Exception {
        HttpResponse<String> response = send("OPTIONS", "/api/enfants", Map.of(
                "Origin", "http://site-inconnu.example", "Access-Control-Request-Method", "GET"), null);
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void cors_entetesPresentsSurUnRefus401() throws Exception {
        HttpResponse<String> response = send("GET", "/api/enfants",
                Map.of("Origin", FRONTEND, "Authorization", "Bearer pas-un-jwt"), null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(FRONTEND);
    }

    @Test
    void sante_publique() throws Exception {
        HttpResponse<String> response = send("GET", "/health", Map.of(), null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("status").asText()).isEqualTo("UP");
    }

    @Test
    void documentation_documentsDesServicesRelayesEtSwaggerUiUnique() throws Exception {
        SERVICES.get("enfant").stubFor(get("/v3/api-docs").willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{\"openapi\":\"3.0.1\"}")));

        HttpResponse<String> docs = send("GET", "/api-docs/enfant", Map.of(), null);
        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(json(docs).get("openapi").asText()).isEqualTo("3.0.1");

        HttpResponse<String> config = send("GET", "/v3/api-docs/swagger-config", Map.of(), null);
        assertThat(config.statusCode()).isEqualTo(200);
        assertThat(config.body()).contains("/api-docs/auth", "/api-docs/pilotage");
    }

    // ---------------------------------------------------------------- outils

    private void accountState(long userId, boolean active, boolean mustChangePassword) {
        SERVICES.get("auth").stubFor(get("/internal/users/" + userId + "/state").willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":" + userId + ",\"active\":" + active + ",\"mustChangePassword\":"
                        + mustChangePassword + ",\"role\":\"ADMIN\"}")));
    }

    private void assertServiceUnavailable(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(json(response).get("error").asText()).isEqualTo("SERVICE_UNAVAILABLE");
    }

    private Map<String, String> bearer() {
        return Map.of("Authorization", "Bearer " + token);
    }

    private HttpResponse<String> send(String method, String path, Map<String, String> headers, byte[] body) throws Exception {
        return client.send(request(method, path, headers, body), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest request(String method, String path, Map<String, String> headers, byte[] body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(builder::header);
        return builder.build();
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private record Multipart(String contentType, byte[] body) {
    }

    private static Multipart multipart(byte[] file) throws Exception {
        String boundary = "limite-InternatAdmin-42";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"logo.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(file);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return new Multipart("multipart/form-data; boundary=" + boundary, out.toByteArray());
    }
}
