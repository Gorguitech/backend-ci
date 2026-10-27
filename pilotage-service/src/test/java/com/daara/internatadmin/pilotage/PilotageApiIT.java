package com.daara.internatadmin.pilotage;

import com.daara.internatadmin.common.support.TestJwts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.daara.internatadmin.common.support.TestMessages.fieldNames;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les cinq services appeles sont simules par un meme WireMock (chemins internes distincts).
 * Contrats de dashboard.model.ts, notification.model.ts, enfant.model.ts (EnfantStats) et employe.model.ts (EmployeStats).
 */
@SpringBootTest(properties = "internatadmin.pilotage.max-wait=2500ms")
@AutoConfigureMockMvc
class PilotageApiIT {

    private static final String INTERNAL_KEY = "cle-interne-de-test";
    private static final String GESTIONNAIRE = "Bearer " + TestJwts.token(5, "awa.diop", "GESTIONNAIRE");
    private static final String ADMIN = "Bearer " + TestJwts.token(1, "admin", "ADMIN");
    private static final WireMockServer SERVICES = start();

    private static final String ENFANT_COUNTS = "/internal/enfants/counts";
    private static final String EMPLOYE_COUNTS = "/internal/employes/counts";
    private static final String SUIVI_RESUME = "/internal/paiements/suivi-resume";
    private static final String TOTAUX_PAIEMENTS = "/internal/paiements/totaux-globaux";
    private static final String TOTAUX_COMPTABILITE = "/internal/comptabilite/totaux-globaux";
    private static final String USER_STATS = "/internal/users/stats";

    // reponses par defaut des services : recettes 52000 + 58000, depenses 115000 + 85500, solde global -90500
    private static final Map<String, String> REFERENCE = Map.of(
            ENFANT_COUNTS, "{\"totalActifs\":2,\"totalSortis\":1,\"totalEnVacances\":1,\"nouveauxDuMois\":1}",
            EMPLOYE_COUNTS, "{\"totalActifs\":2,\"totalEnConge\":1,\"totalAnciens\":1}",
            SUIVI_RESUME, "{\"mensualitesPayees\":1,\"mensualitesPartielles\":1,\"mensualitesImpayees\":1,"
                    + "\"inscriptionsImpayees\":2,\"salairesPayes\":1,\"salairesImpayes\":2}",
            TOTAUX_PAIEMENTS, "{\"paiementsEnfants\":52000.00,\"salaires\":115000.00}",
            TOTAUX_COMPTABILITE, "{\"autresRecettes\":58000.00,\"depenses\":85500.00}",
            USER_STATS, "{\"totalUtilisateurs\":3,\"totalActifs\":2,\"totalDesactives\":1,\"totalAdmins\":1,"
                    + "\"totalGestionnaires\":2,\"comptesEnAttenteChangementMdp\":0}");

    private static final Set<String> TS_DASHBOARD = new TreeSet<>(Set.of("nombreEnfantsActifs", "nombreEmployesActifs",
            "totalRecettes", "totalDepenses", "soldeDisponible", "nombreImpayesMoisCourant"));
    private static final Set<String> TS_NOTIFICATION = new TreeSet<>(Set.of("type", "severity", "message", "count"));
    private static final Set<String> TS_ENFANT_STATS = new TreeSet<>(Set.of("totalActifs", "totalSortis", "totalEnVacances",
            "nouveauxCeMois", "impayesCeMois"));
    private static final Set<String> TS_EMPLOYE_STATS = new TreeSet<>(Set.of("totalActifs", "totalEnConge", "totalAnciens",
            "salairesPayesCeMois", "salairesRestants"));

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

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
        for (String service : List.of("auth", "enfant", "personnel", "paiement", "comptabilite")) {
            registry.add("internatadmin.services." + service, SERVICES::baseUrl);
        }
    }

    @BeforeEach
    void reset() {
        SERVICES.resetAll();
        circuitBreakerFactory.getCircuitBreakerRegistry().getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        REFERENCE.forEach(this::stub);
    }

    // ---------------------------------------------------------------- tableau de bord

    @Test
    void dashboard_contratEtComposition() throws Exception {
        JsonNode d = json(mvc.perform(get("/api/dashboard").header("Authorization", GESTIONNAIRE)
                .header("X-Request-Id", "req-pilotage-1")).andExpect(status().isOk()));

        assertThat(fieldNames(d)).isEqualTo(TS_DASHBOARD);
        assertThat(d.get("nombreEnfantsActifs").asLong()).isEqualTo(2);
        assertThat(d.get("nombreEmployesActifs").asLong()).isEqualTo(2);
        assertThat(d.get("totalRecettes").decimalValue()).isEqualByComparingTo("110000");
        assertThat(d.get("totalDepenses").decimalValue()).isEqualByComparingTo("200500");
        assertThat(d.get("soldeDisponible").decimalValue()).isEqualByComparingTo("-90500");
        assertThat(d.get("nombreImpayesMoisCourant").asLong()).as("PARTIEL + IMPAYE").isEqualTo(2);

        // chaque appel parallele relaie le jeton, la cle interne et l'identifiant de correlation
        YearMonth now = YearMonth.now();
        for (String path : List.of(ENFANT_COUNTS, EMPLOYE_COUNTS, SUIVI_RESUME, TOTAUX_PAIEMENTS, TOTAUX_COMPTABILITE)) {
            SERVICES.verify(getRequestedFor(urlPathEqualTo(path))
                    .withHeader("Authorization", equalTo(GESTIONNAIRE))
                    .withHeader("X-Internal-Key", equalTo(INTERNAL_KEY))
                    .withHeader("X-Request-Id", equalTo("req-pilotage-1")));
        }
        for (String path : List.of(ENFANT_COUNTS, SUIVI_RESUME)) {
            SERVICES.verify(getRequestedFor(urlPathEqualTo(path))
                    .withQueryParam("year", equalTo(String.valueOf(now.getYear())))
                    .withQueryParam("month", equalTo(String.valueOf(now.getMonthValue()))));
        }
        assertThat(SERVICES.findAll(getRequestedFor(urlPathEqualTo(USER_STATS)))).isEmpty();
    }

    @Test
    void dashboard_appelsEnParallele() throws Exception {
        // chauffe (MockMvc, clients Feign crees au premier appel) pour ne mesurer que les appels
        mvc.perform(get("/api/dashboard").header("Authorization", GESTIONNAIRE)).andExpect(status().isOk());

        // 5 appels de 1,5 s chacun : environ 1,5 s en parallele, 7,5 s s'ils etaient enchaines
        for (String path : List.of(ENFANT_COUNTS, EMPLOYE_COUNTS, SUIVI_RESUME, TOTAUX_PAIEMENTS, TOTAUX_COMPTABILITE)) {
            stub(path, aResponse().withHeader("Content-Type", "application/json").withBody(REFERENCE.get(path))
                    .withFixedDelay(1500));
        }
        long start = System.nanoTime();
        mvc.perform(get("/api/dashboard").header("Authorization", GESTIONNAIRE)).andExpect(status().isOk());
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2400));
    }

    @Test
    void dashboard_chaqueDependanceEnPanne_503JamaisDeZeros() throws Exception {
        for (String path : List.of(ENFANT_COUNTS, EMPLOYE_COUNTS, SUIVI_RESUME, TOTAUX_PAIEMENTS, TOTAUX_COMPTABILITE)) {
            reset();
            stub(path, aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));
            expect503(get("/api/dashboard").header("Authorization", GESTIONNAIRE), path + " coupe");

            reset();
            stub(path, aResponse().withStatus(500));
            expect503(get("/api/dashboard").header("Authorization", GESTIONNAIRE), path + " en erreur 500");
        }
    }

    @Test
    void dashboard_reponseLenteOuIncomplete_503() throws Exception {
        // reponse de 4 s : coupee par l'attente maximale (2,5 s ici) ou, a defaut, par le delai de lecture Feign (3 s)
        stub(TOTAUX_COMPTABILITE, aResponse().withFixedDelay(4000).withHeader("Content-Type", "application/json")
                .withBody("{\"autresRecettes\":0,\"depenses\":0}"));
        expect503(get("/api/dashboard").header("Authorization", GESTIONNAIRE), "reponse de 4 s");

        // sous le delai de lecture mais au-dela de l'attente maximale de la requete (2,5 s dans ce test)
        reset();
        stub(EMPLOYE_COUNTS, aResponse().withFixedDelay(2800).withHeader("Content-Type", "application/json")
                .withBody("{\"totalActifs\":2,\"totalEnConge\":1,\"totalAnciens\":1}"));
        expect503(get("/api/dashboard").header("Authorization", GESTIONNAIRE), "attente maximale");

        // champ absent (renomme chez le fournisseur) : jamais lu comme 0
        reset();
        stub(ENFANT_COUNTS, "{\"totalSortis\":1,\"totalEnVacances\":1,\"nouveauxDuMois\":1}");
        expect503(get("/api/dashboard").header("Authorization", GESTIONNAIRE), "champ absent");
    }

    @Test
    void dependanceEnErreurDeRequete_400Relaye() throws Exception {
        stub(ENFANT_COUNTS, aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"INVALID_REQUEST\",\"message\":\"Mois invalide : 13\"}"));
        mvc.perform(get("/api/enfants/stats").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Mois invalide : 13"));
    }

    // ---------------------------------------------------------------- notifications

    @Test
    void notifications_gestionnaire_sansLesDeuxNotificationsAdmin() throws Exception {
        JsonNode items = json(mvc.perform(get("/api/notifications").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk()));

        assertThat(fieldNames(items.get(0))).isEqualTo(TS_NOTIFICATION);
        assertThat(notifications(items)).containsExactly(
                "MENSUALITES_IMPAYEES|WARNING|1 enfant(s) n'ont pas encore payé leur mensualité ce mois-ci|1",
                "MENSUALITES_PARTIELLES|INFO|1 enfant(s) ont un paiement partiel ce mois-ci|1",
                "INSCRIPTIONS_IMPAYEES|WARNING|2 enfant(s) n'ont jamais payé leurs frais d'inscription|2",
                "SALAIRES_IMPAYES|WARNING|2 employé(s) n'ont pas encore reçu leur salaire ce mois-ci|2");
        for (String path : List.of(USER_STATS, TOTAUX_PAIEMENTS, TOTAUX_COMPTABILITE)) {
            assertThat(SERVICES.findAll(getRequestedFor(urlPathEqualTo(path)))).as(path).isEmpty();
        }
    }

    @Test
    void notifications_admin_soldeNegatifEtComptesDesactives() throws Exception {
        JsonNode items = json(mvc.perform(get("/api/notifications").header("Authorization", ADMIN))
                .andExpect(status().isOk()));

        assertThat(notifications(items)).containsExactly(
                "MENSUALITES_IMPAYEES|WARNING|1 enfant(s) n'ont pas encore payé leur mensualité ce mois-ci|1",
                "MENSUALITES_PARTIELLES|INFO|1 enfant(s) ont un paiement partiel ce mois-ci|1",
                "INSCRIPTIONS_IMPAYEES|WARNING|2 enfant(s) n'ont jamais payé leurs frais d'inscription|2",
                "SALAIRES_IMPAYES|WARNING|2 employé(s) n'ont pas encore reçu leur salaire ce mois-ci|2",
                "SOLDE_NEGATIF|DANGER|La trésorerie est négative (-90500.00)|1",
                "COMPTES_DESACTIVES|INFO|1 compte(s) utilisateur désactivé(s)|1");
    }

    @Test
    void notifications_rienASignaler_listeVide() throws Exception {
        stub(SUIVI_RESUME, "{\"mensualitesPayees\":3,\"mensualitesPartielles\":0,\"mensualitesImpayees\":0,"
                + "\"inscriptionsImpayees\":0,\"salairesPayes\":2,\"salairesImpayes\":0}");
        stub(TOTAUX_COMPTABILITE, "{\"autresRecettes\":63000.00,\"depenses\":0}");
        stub(USER_STATS, "{\"totalUtilisateurs\":1,\"totalActifs\":1,\"totalDesactives\":0,\"totalAdmins\":1,"
                + "\"totalGestionnaires\":0,\"comptesEnAttenteChangementMdp\":0}");

        // 52000 + 63000 - 115000 - 0 = 0 : un solde nul n'est pas negatif
        mvc.perform(get("/api/notifications").header("Authorization", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void notifications_authServiceEnPanne_503PourLAdminSeulement() throws Exception {
        stub(USER_STATS, aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));
        expect503(get("/api/notifications").header("Authorization", ADMIN), "auth-service coupe");
        mvc.perform(get("/api/notifications").header("Authorization", GESTIONNAIRE)).andExpect(status().isOk());

        stub(SUIVI_RESUME, aResponse().withStatus(503));
        expect503(get("/api/notifications").header("Authorization", GESTIONNAIRE), "paiement-service en 503");
    }

    // ---------------------------------------------------------------- statistiques des modules

    @Test
    void statsEnfantsEtEmployes_contratsEtValeurs() throws Exception {
        JsonNode enfants = json(mvc.perform(get("/api/enfants/stats").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk()));
        assertThat(fieldNames(enfants)).isEqualTo(TS_ENFANT_STATS);
        assertThat(objectMapper.convertValue(enfants, Map.class)).isEqualTo(Map.of("totalActifs", 2, "totalSortis", 1,
                "totalEnVacances", 1, "nouveauxCeMois", 1, "impayesCeMois", 2));

        JsonNode employes = json(mvc.perform(get("/api/employes/stats").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk()));
        assertThat(fieldNames(employes)).isEqualTo(TS_EMPLOYE_STATS);
        assertThat(objectMapper.convertValue(employes, Map.class)).isEqualTo(Map.of("totalActifs", 2, "totalEnConge", 1,
                "totalAnciens", 1, "salairesPayesCeMois", 1, "salairesRestants", 2));

        stub(EMPLOYE_COUNTS, aResponse().withFault(Fault.EMPTY_RESPONSE));
        expect503(get("/api/employes/stats").header("Authorization", GESTIONNAIRE), "personnel-service coupe");
        stub(SUIVI_RESUME, aResponse().withStatus(500));
        expect503(get("/api/enfants/stats").header("Authorization", GESTIONNAIRE), "paiement-service en erreur");
    }

    // ---------------------------------------------------------------- securite et documentation

    @Test
    void securite_santeEtDocumentation() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isForbidden());
        String expired = TestJwts.token(5, "awa.diop", "GESTIONNAIRE", Instant.now().minusSeconds(3600));
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
        assertThat(SERVICES.getAllServeEvents()).as("aucun appel sans jeton valide").isEmpty();

        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/dashboard'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard'].get.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/notifications'].get.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/notifications'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/enfants/stats'].get.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/employes/stats'].get.responses['503']").exists())
                .andExpect(jsonPath("$.components.schemas.GlobalDashboardResponse.properties.soldeDisponible").exists())
                .andExpect(jsonPath("$.components.schemas.NotificationItem.properties.severity").exists());
    }

    // ---------------------------------------------------------------- outils

    private void stub(String path, String json) {
        stub(path, aResponse().withHeader("Content-Type", "application/json").withBody(json));
    }

    private void stub(String path, ResponseDefinitionBuilder response) {
        SERVICES.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo(path)).willReturn(response));
    }

    private void expect503(org.springframework.test.web.servlet.RequestBuilder request, String cas) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(response.getStatus()).as(cas + " : " + body).isEqualTo(503);
        JsonNode node = objectMapper.readTree(body);
        assertThat(node.path("error").asText()).as(cas + " : " + body).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(fieldNames(node)).as(cas + " : corps d'erreur seul, sans valeur partielle").containsExactly("error", "message");
    }

    private static List<String> notifications(JsonNode items) {
        List<String> lines = new java.util.ArrayList<>();
        items.forEach(i -> lines.add(i.get("type").asText() + "|" + i.get("severity").asText() + "|"
                + i.get("message").asText() + "|" + i.get("count").asLong()));
        return lines;
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
