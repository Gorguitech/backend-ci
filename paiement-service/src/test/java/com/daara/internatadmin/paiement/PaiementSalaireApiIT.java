package com.daara.internatadmin.paiement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.daara.internatadmin.common.support.TestMessages.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Salaires : regles du monolithe et contrats PaiementSalaire, RecuSalaire et SalaireSuivi de paiement.model.ts. */
class PaiementSalaireApiIT extends AbstractPaiementIT {

    private static final Set<String> TS_PAIEMENT_SALAIRE = new TreeSet<>(Set.of("id", "employeId", "employeFullName",
            "employeMatricule", "paymentMethod", "amount", "concernedMonth", "paymentDate", "registeredByUsername", "status"));
    private static final Set<String> TS_RECU_SALAIRE = new TreeSet<>(Set.of("paiementId", "employeMatricule",
            "employeFullName", "employeFunction", "paymentMethod", "amount", "concernedMonth", "paymentDate",
            "registeredByUsername"));
    private static final Set<String> TS_SALAIRE_SUIVI = new TreeSet<>(Set.of("employeId", "matricule", "fullName",
            "montantAttendu", "statut", "paymentDate", "paiementId"));

    @Test
    void salaire_montantEgalAuSalaireDeBase_contratPaiementSalaire() throws Exception {
        stubEmploye(employe(4, "Serigne", "Mbaye", 75000));

        JsonNode p = json(postJson("/api/paiements-salaires", salaire(4, 10)).andExpect(status().isCreated()));

        assertThat(fieldNames(p)).isEqualTo(TS_PAIEMENT_SALAIRE);
        assertThat(p.get("employeId").asLong()).isEqualTo(4);
        assertThat(p.get("employeFullName").asText()).isEqualTo("Serigne Mbaye");
        assertThat(p.get("employeMatricule").asText()).isEqualTo("EMP-2026-0004");
        assertThat(p.get("paymentMethod").asText()).isEqualTo("VIREMENT");
        assertThat(p.get("amount").decimalValue()).isEqualByComparingTo("75000");
        assertThat(p.get("concernedMonth").asText()).isEqualTo("2026-10-01");
        assertThat(p.get("paymentDate").asText()).isEqualTo("2026-10-30");
        assertThat(p.get("registeredByUsername").asText()).isEqualTo("awa.diop");
        assertThat(p.get("status").asText()).isEqualTo("VALIDE");
    }

    @Test
    void salaireDejaPayePourLeMois_409_puisAnnulationEtNouveauPaiement() throws Exception {
        stubEmploye(employe(4, "Serigne", "Mbaye", 75000));
        long id = json(postJson("/api/paiements-salaires", salaire(4, 10))).get("id").asLong();

        postJson("/api/paiements-salaires", salaire(4, 10))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PAYMENT_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Le salaire de 10/2026 a déjà été enregistré pour cet employé"));
        postJson("/api/paiements-salaires", salaire(4, 9)).andExpect(status().isCreated());

        mvc.perform(patch("/api/paiements-salaires/" + id + "/cancel").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANNULE"));
        mvc.perform(patch("/api/paiements-salaires/" + id + "/cancel").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ce paiement est déjà annulé"));
        postJson("/api/paiements-salaires", salaire(4, 10)).andExpect(status().isCreated());

        // la seconde annulation et le doublon, refuses, ne sont pas traces
        assertThat(drainAudit()).extracting(e -> e.get("action").asText() + "/" + e.get("entityType").asText())
                .containsExactlyInAnyOrder("CREATE/PaiementSalaire", "CREATE/PaiementSalaire", "CREATE/PaiementSalaire",
                        "CANCEL/PaiementSalaire");
    }

    @Test
    void recuImpressionHistoriqueEtAudit() throws Exception {
        stubEmploye(employe(4, "Serigne", "Mbaye", 75000));
        long id = json(postJson("/api/paiements-salaires", salaire(4, 10))).get("id").asLong();

        JsonNode recu = json(mvc.perform(get("/api/paiements-salaires/" + id + "/receipt-data").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk()));
        assertThat(fieldNames(recu)).isEqualTo(TS_RECU_SALAIRE);
        assertThat(recu.get("employeFunction").asText()).isEqualTo("MAITRE_CORANIQUE");
        assertThat(recu.get("amount").decimalValue()).isEqualByComparingTo("75000");

        mvc.perform(post("/api/paiements-salaires/" + id + "/print-receipt").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.message").value("Impression du reçu enregistrée pour le salaire de Serigne Mbaye"));
        mvc.perform(get("/api/paiements-salaires/employe/4").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/paiements-salaires/999999/receipt-data").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Paiement introuvable : 999999"));

        assertThat(drainAudit()).extracting(e -> e.get("action").asText() + "/" + e.get("entityType").asText())
                .containsExactlyInAnyOrder("CREATE/PaiementSalaire", "PRINT_RECEIPT/PaiementSalaire");
    }

    @Test
    void suiviDesSalaires_contratEtStatuts() throws Exception {
        stubEmploye(employe(1, "Serigne", "Mbaye", 75000));
        stubEmployesSuivis(List.of(employe(1, "Serigne", "Mbaye", 75000), employe(2, "Fatou", "Gueye", 40000)));
        postJson("/api/paiements-salaires", salaire(1, 10));

        JsonNode suivi = json(mvc.perform(get("/api/paiements-salaires?year=2026&month=10&status=ALL")
                .header("Authorization", GESTIONNAIRE)).andExpect(status().isOk()));
        assertThat(fieldNames(suivi.get(0))).isEqualTo(TS_SALAIRE_SUIVI);
        assertThat(suivi.findValuesAsText("fullName")).containsExactly("Fatou Gueye", "Serigne Mbaye");
        assertThat(suivi.findValuesAsText("statut")).containsExactly("IMPAYE", "PAYE");
        assertThat(suivi.get(0).get("paymentDate").isNull()).isTrue();
        assertThat(suivi.get(0).get("montantAttendu").decimalValue()).isEqualByComparingTo("40000");

        mvc.perform(get("/api/paiements-salaires?year=2026&month=10&status=paye").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void employeInconnu_400AvecLeMessageDePersonnelService() throws Exception {
        PERSONNEL_SERVICE.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/internal/employes/99")
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"INVALID_REQUEST\",\"message\":\"Employé introuvable : 99\"}")));

        postJson("/api/paiements-salaires", salaire(99, 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Employé introuvable : 99"));
    }

    @Test
    void validations400() throws Exception {
        postJson("/api/paiements-salaires", Map.of("employeId", 4, "paymentMethod", "VIREMENT", "concernedYear", 2026))
                .andExpect(status().isBadRequest());
        postJson("/api/paiements-salaires", Map.of("employeId", 4, "paymentMethod", "VIREMENT", "concernedYear", 2026,
                "concernedMonth", 0)).andExpect(status().isBadRequest());
    }

    private static Map<String, Object> salaire(long employeId, int month) {
        return Map.of("employeId", employeId, "paymentMethod", "VIREMENT", "concernedYear", 2026,
                "concernedMonth", month, "paymentDate", "2026-10-30");
    }
}
