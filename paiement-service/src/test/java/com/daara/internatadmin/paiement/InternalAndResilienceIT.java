package com.daara.internatadmin.paiement;

import com.daara.internatadmin.common.security.InternalApiKeyFilter;
import com.daara.internatadmin.common.support.TestJwts;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pannes d'enfant-service (Feign, circuit breaker), endpoints internes, securite et documentation. */
class InternalAndResilienceIT extends AbstractPaiementIT {

    // ---------------------------------------------------------------- resilience des appels Feign

    @Test
    void eleveInconnu_400RelayeEtCircuitJamaisOuvert() throws Exception {
        ENFANT_SERVICE.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/internal/enfants/99")
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"INVALID_REQUEST\",\"message\":\"Enfant introuvable : 99\"}")));
        stubEnfant(enfant(7, "Moussa", "Ndiaye", 15000, 10000));

        // IllegalArgumentException ignoree par le circuit : 15 erreurs utilisateur ne l'ouvrent pas
        for (int i = 0; i < 15; i++) {
            postJson("/api/paiements-enfants", mensualite(99, 10000, false))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value("Enfant introuvable : 99"));
        }
        postJson("/api/paiements-enfants", mensualite(7, 10000, false)).andExpect(status().isCreated());
    }

    @Test
    void enfantServiceEnErreurOuInjoignable_503EtJamais409() throws Exception {
        ENFANT_SERVICE.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/internal/enfants/7")
                .willReturn(aResponse().withStatus(500)));
        postJson("/api/paiements-enfants", mensualite(7, 6000, false))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));

        ENFANT_SERVICE.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/internal/enfants/7")
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        postJson("/api/paiements-enfants", mensualite(7, 6000, false))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
        mvc.perform(get("/api/paiements-enfants/mensualites?year=2026&month=10").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void enfantServiceTropLent_503ApresLeDelaiDeLecture() throws Exception {
        ENFANT_SERVICE.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/internal/enfants/7")
                .willReturn(aResponse().withFixedDelay(5000).withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(enfant(7, "Moussa", "Ndiaye", 15000, 10000)))));

        postJson("/api/paiements-enfants", mensualite(7, 10000, false))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void enfantServiceLentMaisDansLeDelai_accepte() throws Exception {
        // 1,5 s : au-dela de la limite de 1 s par defaut de Spring Cloud CircuitBreaker, en deca du delai de lecture
        ENFANT_SERVICE.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/internal/enfants/7")
                .willReturn(aResponse().withFixedDelay(1500).withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(enfant(7, "Moussa", "Ndiaye", 15000, 10000)))));

        postJson("/api/paiements-enfants", mensualite(7, 10000, false)).andExpect(status().isCreated());
    }

    @Test
    void enfantServiceDurablementEnPanne_circuitOuvertSansNouvelAppel() throws Exception {
        ENFANT_SERVICE.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/internal/enfants/7")
                .willReturn(aResponse().withStatus(500)));
        for (int i = 0; i < 10; i++) {
            postJson("/api/paiements-enfants", mensualite(7, 6000, false)).andExpect(status().isServiceUnavailable());
        }
        ENFANT_SERVICE.resetRequests();

        postJson("/api/paiements-enfants", mensualite(7, 6000, false))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
        ENFANT_SERVICE.verify(0, getRequestedFor(urlEqualTo("/internal/enfants/7")));
    }

    // ---------------------------------------------------------------- endpoints internes

    @Test
    void totauxMensuelsEtGlobaux_reglesDeLaTresorerieDuMonolithe() throws Exception {
        stubEnfant(enfant(7, "Moussa", "Ndiaye", 15000, 10000));
        stubEmploye(employe(4, "Serigne", "Mbaye", 75000));
        postJson("/api/paiements-enfants", mensualite(7, 10000, false));                     // paye le 2026-10-05
        postJson("/api/paiements-enfants", inscription(7, 15000));                          // paye le 2026-09-28
        long annule = json(postJson("/api/paiements-salaires", Map.of("employeId", 4, "paymentMethod", "ESPECES",
                "concernedYear", 2026, "concernedMonth", 9, "paymentDate", "2026-10-02"))).get("id").asLong();
        postJson("/api/paiements-salaires", Map.of("employeId", 4, "paymentMethod", "ESPECES",
                "concernedYear", 2026, "concernedMonth", 10, "paymentDate", "2026-11-02"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/paiements-salaires/" + annule + "/cancel")
                .header("Authorization", GESTIONNAIRE));

        // eleves par date de paiement, salaires par mois concerne, annules exclus
        internal("/internal/paiements/totaux-mensuels?year=2026&month=10").andExpect(status().isOk())
                .andExpect(jsonPath("$.paiementsEnfants").value(10000.0))
                .andExpect(jsonPath("$.salaires").value(75000.0));
        internal("/internal/paiements/totaux-mensuels?year=2026&month=9")
                .andExpect(jsonPath("$.paiementsEnfants").value(15000.0))
                .andExpect(jsonPath("$.salaires").value(0));
        internal("/internal/paiements/totaux-globaux")
                .andExpect(jsonPath("$.paiementsEnfants").value(25000.0))
                .andExpect(jsonPath("$.salaires").value(75000.0));
    }

    @Test
    void resumeDuSuivi_pourLesStatistiquesEtLesNotifications() throws Exception {
        stubEnfant(enfant(1, "Moussa", "Ndiaye", 15000, 10000));
        stubEnfant(enfant(2, "Awa", "Fall", 15000, 10000));
        stubEnfantsSuivis(List.of(enfant(1, "Moussa", "Ndiaye", 15000, 10000), enfant(2, "Awa", "Fall", 15000, 10000),
                enfant(3, "Ibou", "Sow", 15000, 10000)));
        stubEmploye(employe(1, "Serigne", "Mbaye", 75000));
        stubEmployesSuivis(List.of(employe(1, "Serigne", "Mbaye", 75000), employe(2, "Fatou", "Gueye", 40000)));
        postJson("/api/paiements-enfants", mensualite(1, 10000, false));
        postJson("/api/paiements-enfants", mensualite(2, 6000, true));
        postJson("/api/paiements-enfants", inscription(1, 15000));
        postJson("/api/paiements-salaires", Map.of("employeId", 1, "paymentMethod", "ESPECES", "concernedYear", 2026,
                "concernedMonth", 10));

        internal("/internal/paiements/suivi-resume?year=2026&month=10").andExpect(status().isOk())
                .andExpect(jsonPath("$.mensualitesPayees").value(1))
                .andExpect(jsonPath("$.mensualitesPartielles").value(1))
                .andExpect(jsonPath("$.mensualitesImpayees").value(1))
                .andExpect(jsonPath("$.inscriptionsImpayees").value(2))
                .andExpect(jsonPath("$.salairesPayes").value(1))
                .andExpect(jsonPath("$.salairesImpayes").value(1));
    }

    @Test
    void interne_cleObligatoire() throws Exception {
        mvc.perform(get("/internal/paiements/totaux-globaux").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("INTERNAL_ACCESS_DENIED"));
    }

    // ---------------------------------------------------------------- securite, sante, documentation

    @Test
    void securite_sansJeton403_jetonExpire401() throws Exception {
        mvc.perform(get("/api/paiements-enfants/inscriptions")).andExpect(status().isForbidden());
        String expired = TestJwts.token(5, "awa.diop", "GESTIONNAIRE", Instant.now().minusSeconds(3600));
        mvc.perform(get("/api/paiements-salaires").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void sante_etDocumentationOpenApi() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/paiements-enfants'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-enfants'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-enfants'].post.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-enfants/{id}/cancel'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-enfants/{id}/receipt-data'].get").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-enfants/{id}/print-receipt'].post").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-enfants/enfant/{enfantId}/inscription'].get").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-enfants/enfant/{enfantId}/mensualites'].get").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-enfants/mensualites'].get").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-enfants/inscriptions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-salaires'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-salaires'].get").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-salaires/{id}/cancel'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-salaires/{id}/receipt-data'].get").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-salaires/{id}/print-receipt'].post").exists())
                .andExpect(jsonPath("$.paths['/api/paiements-salaires/employe/{employeId}'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/paiements/totaux-globaux']").doesNotExist());
    }

    private org.springframework.test.web.servlet.ResultActions internal(String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", GESTIONNAIRE).header(InternalApiKeyFilter.HEADER, INTERNAL_KEY));
    }
}
