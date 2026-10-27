package com.daara.internatadmin.comptabilite;

import com.daara.internatadmin.common.audit.AuditMessaging;
import com.daara.internatadmin.common.security.InternalApiKeyFilter;
import com.daara.internatadmin.common.support.InternatAdminContainers;
import com.daara.internatadmin.common.support.TestJwts;
import com.daara.internatadmin.common.support.TestMessages;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.time.Instant;
import java.util.HashMap;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PostgreSQL et RabbitMQ reels, paiement-service simule par WireMock ; contrats de finance.model.ts. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(InternatAdminContainers.class)
class ComptabiliteApiIT {

    private static final String INTERNAL_KEY = "cle-interne-de-test";
    private static final String GESTIONNAIRE = "Bearer " + TestJwts.token(5, "awa.diop", "GESTIONNAIRE");
    private static final String AUTRE_GESTIONNAIRE = "Bearer " + TestJwts.token(6, "moussa.sow", "GESTIONNAIRE");
    private static final String ADMIN = "Bearer " + TestJwts.token(1, "admin", "ADMIN");
    private static final WireMockServer PAIEMENT_SERVICE = start();

    private static final Set<String> TS_DEPENSE = new TreeSet<>(Set.of("id", "categorieId", "categorieName", "amount",
            "expenseDate", "description", "registeredByUsername", "status"));
    private static final Set<String> TS_CATEGORIE = new TreeSet<>(Set.of("id", "name", "description", "active"));
    private static final Set<String> TS_AUTRE_RECETTE = new TreeSet<>(Set.of("id", "source", "amount", "recetteDate",
            "description", "registeredByUsername", "status"));
    private static final Set<String> TS_TRESORERIE = new TreeSet<>(Set.of("year", "month", "totalRecettes",
            "totalDepenses", "solde", "detailRecettes", "detailDepenses"));
    private static final Set<String> TS_LIGNE_DETAIL = new TreeSet<>(Set.of("source", "montant"));

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
        registry.add("internatadmin.services.paiement", PAIEMENT_SERVICE::baseUrl);
    }

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM depenses");
        jdbc.update("DELETE FROM autres_recettes");
        jdbc.update("DELETE FROM categories_depenses WHERE name NOT IN ('Riz', 'Huile', 'Sucre', 'Gaz', 'Eau/Électricité', "
                + "'Santé', 'Petit Matériel', 'Transport')");
        jdbc.update("UPDATE categories_depenses SET active = TRUE");
        amqpAdmin.purgeQueue(AuditMessaging.QUEUE, false);
        PAIEMENT_SERVICE.resetAll();
        circuitBreakerFactory.getCircuitBreakerRegistry().getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    // ---------------------------------------------------------------- categories

    @Test
    void categories_amorcageDuMonolitheEtContrat() throws Exception {
        JsonNode all = json(mvc.perform(get("/api/categories-depenses?status=ALL").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk()));
        assertThat(all.findValuesAsText("name")).containsExactlyInAnyOrder(
                "Riz", "Huile", "Sucre", "Gaz", "Eau/Électricité", "Santé", "Petit Matériel", "Transport");
        assertThat(fieldNames(all.get(0))).isEqualTo(TS_CATEGORIE);
        assertThat(all.get(0).get("description").isNull()).as("description null pour les categories initiales").isTrue();
    }

    @Test
    void categories_creationEtDesactivationParLAdmin_filtres() throws Exception {
        JsonNode created = json(postJson("/api/categories-depenses", ADMIN, Map.of("name", "Fournitures", "description", "Cahiers"))
                .andExpect(status().isCreated()));
        assertThat(fieldNames(created)).isEqualTo(TS_CATEGORIE);
        assertThat(created.get("active").asBoolean()).isTrue();

        postJson("/api/categories-depenses", GESTIONNAIRE, Map.of("name", "Autre")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/categories-depenses/" + created.get("id").asLong() + "/deactivate").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/categories-depenses/" + created.get("id").asLong() + "/deactivate").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/categories-depenses").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.length()").value(8));
        mvc.perform(get("/api/categories-depenses?status=INACTIVE").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Fournitures"));
        mvc.perform(patch("/api/categories-depenses/999999/deactivate").header("Authorization", ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Catégorie introuvable : 999999"));
    }

    @Test
    void categories_nomDejaPris_400AuLieuDu500DuMonolithe() throws Exception {
        postJson("/api/categories-depenses", ADMIN, Map.of("name", "Riz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("La catégorie 'Riz' existe déjà"));
        postJson("/api/categories-depenses", ADMIN, Map.of("name", "")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- depenses

    @Test
    void depense_contratEtDoublonSurDeuxMinutes() throws Exception {
        long riz = categorieId("Riz");
        Map<String, Object> body = depense(riz, 25000, "2026-10-12");

        JsonNode created = json(postJson("/api/depenses", GESTIONNAIRE, body).andExpect(status().isCreated()));
        assertThat(fieldNames(created)).isEqualTo(TS_DEPENSE);
        assertThat(created.get("categorieName").asText()).isEqualTo("Riz");
        assertThat(created.get("amount").decimalValue()).isEqualByComparingTo("25000");
        assertThat(created.get("expenseDate").asText()).isEqualTo("2026-10-12");
        assertThat(created.get("registeredByUsername").asText()).isEqualTo("awa.diop");
        assertThat(created.get("status").asText()).isEqualTo("VALIDE");

        // le formulaire teste error === 'POSSIBLE_DUPLICATE' et affiche le message pour confirmation
        postJson("/api/depenses", GESTIONNAIRE, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("POSSIBLE_DUPLICATE"))
                .andExpect(jsonPath("$.message").value("Une dépense identique (même catégorie, montant et date) vient d'être "
                        + "enregistrée il y a moins de 2 minutes. Si c'est intentionnel, renvoyez la requête avec confirmDuplicate=true."));

        Map<String, Object> confirmed = new HashMap<>(body);
        confirmed.put("confirmDuplicate", true);
        postJson("/api/depenses", GESTIONNAIRE, confirmed).andExpect(status().isCreated());

        // un autre utilisateur n'est pas concerne par le doublon
        postJson("/api/depenses", AUTRE_GESTIONNAIRE, body).andExpect(status().isCreated());

        // au-dela de 2 minutes, la meme depense n'est plus un doublon
        jdbc.update("UPDATE depenses SET created_at = created_at - INTERVAL '3 minutes'");
        postJson("/api/depenses", GESTIONNAIRE, body).andExpect(status().isCreated());
    }

    @Test
    void depense_categorieDesactiveeOuInconnue_400() throws Exception {
        jdbc.update("UPDATE categories_depenses SET active = FALSE WHERE name = 'Gaz'");
        postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Gaz"), 1000, "2026-10-12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("La catégorie 'Gaz' est désactivée et ne peut plus être utilisée"));
        postJson("/api/depenses", GESTIONNAIRE, depense(999999, 1000, "2026-10-12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Catégorie introuvable : 999999"));
        postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Riz"), 0, "2026-10-12")).andExpect(status().isBadRequest());
    }

    @Test
    void depense_listeParMoisEtCategorie_annulationRecuImpressionAudit() throws Exception {
        long riz = categorieId("Riz");
        long gaz = categorieId("Gaz");
        long d1 = id(postJson("/api/depenses", GESTIONNAIRE, depense(riz, 25000, "2026-10-12")));
        postJson("/api/depenses", GESTIONNAIRE, depense(gaz, 12000, "2026-10-13"));
        postJson("/api/depenses", GESTIONNAIRE, depense(riz, 5000, "2026-09-30"));

        mvc.perform(get("/api/depenses?year=2026&month=10").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/depenses?year=2026&month=10&categorieId=" + riz).header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categorieName").value("Riz"));

        JsonNode recu = json(mvc.perform(get("/api/depenses/" + d1 + "/receipt-data").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk()));
        assertThat(fieldNames(recu)).isEqualTo(TS_DEPENSE);
        mvc.perform(post("/api/depenses/" + d1 + "/print-receipt").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.message").value("Impression du reçu enregistrée pour la dépense #" + d1));

        mvc.perform(patch("/api/depenses/" + d1 + "/cancel").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ANNULE"));
        mvc.perform(patch("/api/depenses/" + d1 + "/cancel").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Cette dépense est déjà annulée"));
        mvc.perform(get("/api/depenses?year=2026&month=10").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/depenses/999999/receipt-data").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Dépense introuvable : 999999"));

        assertThat(TestMessages.drainAudit(rabbitTemplate, objectMapper)).extracting(e -> e.get("action").asText())
                .containsExactlyInAnyOrder("CREATE", "CREATE", "CREATE", "PRINT_RECEIPT", "CANCEL");
    }

    // ---------------------------------------------------------------- autres recettes

    @Test
    void recette_contratDoublonListeEtAnnulation() throws Exception {
        Map<String, Object> body = Map.of("source", "Don de la mairie", "amount", 50000, "recetteDate", "2026-10-05");

        JsonNode created = json(postJson("/api/autres-recettes", GESTIONNAIRE, body).andExpect(status().isCreated()));
        assertThat(fieldNames(created)).isEqualTo(TS_AUTRE_RECETTE);
        assertThat(created.get("description").isNull()).isTrue();

        postJson("/api/autres-recettes", GESTIONNAIRE, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("POSSIBLE_DUPLICATE"))
                .andExpect(jsonPath("$.message").value("Une recette identique (même source, montant et date) vient d'être "
                        + "enregistrée il y a moins de 2 minutes. Si c'est intentionnel, renvoyez la requête avec confirmDuplicate=true."));
        Map<String, Object> confirmed = new HashMap<>(body);
        confirmed.put("confirmDuplicate", true);
        postJson("/api/autres-recettes", GESTIONNAIRE, confirmed).andExpect(status().isCreated());

        mvc.perform(get("/api/autres-recettes?year=2026&month=10").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.length()").value(2));
        long id = created.get("id").asLong();
        mvc.perform(post("/api/autres-recettes/" + id + "/print-receipt").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.message").value("Impression du reçu enregistrée pour la recette #" + id));
        assertThat(fieldNames(json(mvc.perform(get("/api/autres-recettes/" + id + "/receipt-data")
                .header("Authorization", GESTIONNAIRE))))).isEqualTo(TS_AUTRE_RECETTE);
        mvc.perform(patch("/api/autres-recettes/" + id + "/cancel").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.status").value("ANNULE"));
        mvc.perform(patch("/api/autres-recettes/" + id + "/cancel").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Cette recette est déjà annulée"));
        postJson("/api/autres-recettes", GESTIONNAIRE, Map.of("source", "", "amount", 10)).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- tresorerie

    @Test
    void tresorerie_contratLibellesEtCalculs() throws Exception {
        stubTotauxPaiements("110000.00", "115000.00");
        postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Riz"), 40000, "2026-10-12"));
        postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Gaz"), 12000, "2026-10-13"));
        long annulee = id(postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Huile"), 9000, "2026-10-14")));
        mvc.perform(patch("/api/depenses/" + annulee + "/cancel").header("Authorization", GESTIONNAIRE));
        postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Sucre"), 7000, "2026-11-02"));
        postJson("/api/autres-recettes", GESTIONNAIRE, Map.of("source", "Don", "amount", 50000, "recetteDate", "2026-10-05"));

        JsonNode t = json(mvc.perform(get("/api/tresorerie?year=2026&month=10").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk()));

        assertThat(fieldNames(t)).isEqualTo(TS_TRESORERIE);
        assertThat(fieldNames(t.get("detailRecettes").get(0))).isEqualTo(TS_LIGNE_DETAIL);
        assertThat(t.get("year").asInt()).isEqualTo(2026);
        assertThat(t.get("month").asInt()).isEqualTo(10);
        assertThat(t.get("detailRecettes").findValuesAsText("source")).containsExactly(
                "Paiements des enfants (inscriptions + mensualités)", "Autres recettes (dons, subventions)");
        assertThat(t.get("detailDepenses").findValuesAsText("source")).containsExactly("Salaires du personnel", "Gaz", "Riz");
        assertThat(t.get("totalRecettes").decimalValue()).isEqualByComparingTo("160000");
        assertThat(t.get("totalDepenses").decimalValue()).isEqualByComparingTo("167000");
        assertThat(t.get("solde").decimalValue()).isEqualByComparingTo("-7000");

        PAIEMENT_SERVICE.verify(getRequestedFor(urlPathEqualTo("/internal/paiements/totaux-mensuels"))
                .withQueryParam("year", equalTo("2026")).withQueryParam("month", equalTo("10"))
                .withHeader("Authorization", equalTo(GESTIONNAIRE))
                .withHeader("X-Internal-Key", equalTo(INTERNAL_KEY)));
    }

    /**
     * Jeu de reference, resultat attendu calcule a la main selon TresorerieService du monolithe (octobre 2026).
     *
     * Totaux de paiement-service, calcules a la main selon les memes regles (seule source hors de cette base) :
     *   paiements des eleves VALIDE par date de paiement d'octobre : 10000 + 12000 + 5000 + 10000 = 37000
     *   salaires VALIDE dont le mois concerne est octobre : 75000
     *
     * Autres recettes VALIDE dont la date est en octobre :
     *   Don 50000 (05/10) compte ; Subvention 30000 (15/10) annulee : exclue ; Vente 8000 (01/11) : exclue  => 50000
     * Depenses VALIDE dont la date est en octobre, par categorie :
     *   Riz 40000 (12/10) + 15000 (25/10) = 55000 ; Gaz 12000 (13/10) ; Eau/Électricité 6500 (01/10) ;
     *   Santé 3000 (31/10) ; Huile 9000 (14/10) annulee : exclue ; Sucre 7000 (02/11) et Transport 2000 (30/09) : exclus
     *   total des depenses courantes = 55000 + 12000 + 6500 + 3000 = 76500
     * Lignes de depenses : salaires d'abord, puis categories triees par nom (ordre naturel des chaines) :
     *   Eau/Électricité < Gaz < Riz < Santé
     * Totaux : recettes = 37000 + 50000 = 87000 ; depenses = 75000 + 76500 = 151500 ; solde = 87000 - 151500 = -64500
     */
    @Test
    void tresorerie_jeuDeReferenceCalculeALaMain() throws Exception {
        stubTotauxPaiements("37000.00", "75000.00");
        postJson("/api/autres-recettes", GESTIONNAIRE, Map.of("source", "Don", "amount", 50000, "recetteDate", "2026-10-05"));
        long subvention = id(postJson("/api/autres-recettes", GESTIONNAIRE,
                Map.of("source", "Subvention", "amount", 30000, "recetteDate", "2026-10-15")));
        mvc.perform(patch("/api/autres-recettes/" + subvention + "/cancel").header("Authorization", GESTIONNAIRE));
        postJson("/api/autres-recettes", GESTIONNAIRE, Map.of("source", "Vente", "amount", 8000, "recetteDate", "2026-11-01"));

        postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Riz"), 40000, "2026-10-12"));
        postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Riz"), 15000, "2026-10-25"));
        postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Gaz"), 12000, "2026-10-13"));
        long huile = id(postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Huile"), 9000, "2026-10-14")));
        mvc.perform(patch("/api/depenses/" + huile + "/cancel").header("Authorization", GESTIONNAIRE));
        postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Sucre"), 7000, "2026-11-02"));
        postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Eau/Électricité"), 6500, "2026-10-01"));
        postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Santé"), 3000, "2026-10-31"));
        postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Transport"), 2000, "2026-09-30"));

        JsonNode t = json(mvc.perform(get("/api/tresorerie?year=2026&month=10").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk()));

        assertThat(lignes(t.get("detailRecettes"))).containsExactly(
                "Paiements des enfants (inscriptions + mensualités)=37000",
                "Autres recettes (dons, subventions)=50000");
        assertThat(lignes(t.get("detailDepenses"))).containsExactly(
                "Salaires du personnel=75000",
                "Eau/Électricité=6500",
                "Gaz=12000",
                "Riz=55000",
                "Santé=3000");
        assertThat(t.get("totalRecettes").decimalValue()).isEqualByComparingTo("87000");
        assertThat(t.get("totalDepenses").decimalValue()).isEqualByComparingTo("151500");
        assertThat(t.get("solde").decimalValue()).isEqualByComparingTo("-64500");
    }

    /** Lignes de detail sous la forme "source=montant", montant sans decimales inutiles. */
    private static java.util.List<String> lignes(JsonNode details) {
        java.util.List<String> lignes = new java.util.ArrayList<>();
        details.forEach(l -> lignes.add(l.get("source").asText() + "="
                + l.get("montant").decimalValue().stripTrailingZeros().toPlainString()));
        return lignes;
    }

    @Test
    void tresorerie_paiementServiceIndisponible_503() throws Exception {
        PAIEMENT_SERVICE.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/internal/paiements/totaux-mensuels"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        mvc.perform(get("/api/tresorerie?year=2026&month=10").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));

        PAIEMENT_SERVICE.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/internal/paiements/totaux-mensuels"))
                .willReturn(aResponse().withFixedDelay(5000)));
        mvc.perform(get("/api/tresorerie?year=2026&month=10").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isServiceUnavailable());
    }

    // ---------------------------------------------------------------- interne, securite, documentation

    @Test
    void interne_totauxGlobauxValidesSeulement() throws Exception {
        postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Riz"), 40000, "2026-10-12"));
        long annulee = id(postJson("/api/depenses", GESTIONNAIRE, depense(categorieId("Gaz"), 12000, "2025-01-13")));
        mvc.perform(patch("/api/depenses/" + annulee + "/cancel").header("Authorization", GESTIONNAIRE));
        postJson("/api/autres-recettes", GESTIONNAIRE, Map.of("source", "Don", "amount", 50000, "recetteDate", "2025-03-05"));

        mvc.perform(get("/internal/comptabilite/totaux-globaux").header("Authorization", GESTIONNAIRE)
                        .header(InternalApiKeyFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autresRecettes").value(50000.0))
                .andExpect(jsonPath("$.depenses").value(40000.0));
        mvc.perform(get("/internal/comptabilite/totaux-globaux").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isForbidden());
    }

    @Test
    void securite_santeEtDocumentation() throws Exception {
        mvc.perform(get("/api/depenses")).andExpect(status().isForbidden());
        String expired = TestJwts.token(5, "awa.diop", "GESTIONNAIRE", Instant.now().minusSeconds(3600));
        mvc.perform(get("/api/tresorerie").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/categories-depenses'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/categories-depenses'].get").exists())
                .andExpect(jsonPath("$.paths['/api/categories-depenses/{id}/deactivate'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/depenses'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/depenses'].get").exists())
                .andExpect(jsonPath("$.paths['/api/depenses/{id}/cancel'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/depenses/{id}/receipt-data'].get").exists())
                .andExpect(jsonPath("$.paths['/api/depenses/{id}/print-receipt'].post").exists())
                .andExpect(jsonPath("$.paths['/api/autres-recettes'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/autres-recettes'].get").exists())
                .andExpect(jsonPath("$.paths['/api/autres-recettes/{id}/cancel'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/autres-recettes/{id}/receipt-data'].get").exists())
                .andExpect(jsonPath("$.paths['/api/autres-recettes/{id}/print-receipt'].post").exists())
                .andExpect(jsonPath("$.paths['/api/tresorerie'].get.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/internal/comptabilite/totaux-globaux']").doesNotExist());
    }

    // ---------------------------------------------------------------- outils

    private void stubTotauxPaiements(String paiementsEnfants, String salaires) {
        PAIEMENT_SERVICE.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/internal/paiements/totaux-mensuels"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"paiementsEnfants\":" + paiementsEnfants + ",\"salaires\":" + salaires + "}")));
    }

    private static Map<String, Object> depense(long categorieId, int amount, String date) {
        return Map.of("categorieId", categorieId, "amount", amount, "expenseDate", date, "description", "Achat du marché");
    }

    private long categorieId(String name) {
        return jdbc.queryForObject("SELECT id FROM categories_depenses WHERE name = ?", Long.class, name);
    }

    private ResultActions postJson(String path, String bearer, Object body) throws Exception {
        return mvc.perform(post(path).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private long id(ResultActions result) throws Exception {
        return json(result).get("id").asLong();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
