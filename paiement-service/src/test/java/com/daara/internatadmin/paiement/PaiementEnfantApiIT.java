package com.daara.internatadmin.paiement;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;

import static com.daara.internatadmin.common.support.TestMessages.fieldNames;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Paiements des eleves : regles et 409 attendus par paiement-enfant-form.component.ts, contrats TypeScript. */
class PaiementEnfantApiIT extends AbstractPaiementIT {

    /** Champs de l'interface PaiementEnfant de paiement.model.ts. */
    private static final Set<String> TS_PAIEMENT_ENFANT = new TreeSet<>(Set.of("id", "enfantId", "enfantFullName",
            "enfantMatricule", "paymentType", "paymentMethod", "amount", "concernedMonth", "paymentDate",
            "registeredByUsername", "status", "balanceAfter", "isPartial", "expectedAmount", "excessAmount"));
    /** Champs de RecuPaiement. */
    private static final Set<String> TS_RECU_PAIEMENT = new TreeSet<>(Set.of("paiementId", "enfantMatricule",
            "enfantFullName", "guardianName", "paymentType", "paymentMethod", "amount", "concernedMonth", "paymentDate",
            "balanceAfter", "isPartial", "registeredByUsername", "libelleStatut"));
    /** Champs de SuiviMensuel. */
    private static final Set<String> TS_SUIVI_MENSUEL = new TreeSet<>(Set.of("enfantId", "matricule", "fullName",
            "guardianName", "guardianPhone", "montantDu", "montantPaye", "montantRestant", "excedent", "statutPaiement",
            "paiementIds"));
    /** Champs de InscriptionSuivi. */
    private static final Set<String> TS_INSCRIPTION_SUIVI = new TreeSet<>(Set.of("enfantId", "matricule", "fullName",
            "montantAttendu", "statut", "paymentDate", "paiementId"));

    @Autowired
    DataSource dataSource;

    // ---------------------------------------------------------------- mensualites

    @Test
    void mensualiteComplete_contratPaiementEnfantEtCopiesFigees() throws Exception {
        stubEnfant(enfant(7, "Moussa", "Ndiaye", 15000, 10000));

        JsonNode p = json(postJson("/api/paiements-enfants", mensualite(7, 10000, false)).andExpect(status().isCreated()));

        assertThat(fieldNames(p)).isEqualTo(TS_PAIEMENT_ENFANT);
        assertThat(p.get("enfantId").asLong()).isEqualTo(7);
        assertThat(p.get("enfantFullName").asText()).isEqualTo("Moussa Ndiaye");
        assertThat(p.get("enfantMatricule").asText()).isEqualTo("ENF-2026-0007");
        assertThat(p.get("paymentType").asText()).isEqualTo("MENSUALITE");
        assertThat(p.get("paymentMethod").asText()).isEqualTo("ESPECES");
        assertThat(p.get("amount").decimalValue()).isEqualByComparingTo("10000");
        assertThat(p.get("concernedMonth").asText()).isEqualTo("2026-10-01");
        assertThat(p.get("paymentDate").asText()).isEqualTo("2026-10-05");
        assertThat(p.get("registeredByUsername").asText()).isEqualTo("awa.diop");
        assertThat(p.get("status").asText()).isEqualTo("VALIDE");
        assertThat(p.get("balanceAfter").decimalValue()).isEqualByComparingTo("0");
        assertThat(p.get("isPartial").isBoolean()).isTrue();
        assertThat(p.get("isPartial").asBoolean()).isFalse();
        assertThat(p.get("expectedAmount").decimalValue()).isEqualByComparingTo("10000");
        assertThat(p.get("excessAmount").decimalValue()).isEqualByComparingTo("0");

        // la copie figee survit a un changement d'identite dans enfant-service
        stubEnfant(enfant(7, "Moussa", "Diallo", 15000, 10000));
        mvc.perform(get("/api/paiements-enfants/enfant/7/mensualites").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$[0].enfantFullName").value("Moussa Ndiaye"));
    }

    @Test
    void parcoursPartiel_les409AttendusParLeFormulaire() throws Exception {
        stubEnfant(enfant(7, "Moussa", "Ndiaye", 15000, 10000));

        // montant inferieur sans confirmation : le formulaire propose de confirmer
        postJson("/api/paiements-enfants", mensualite(7, 6000, false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PARTIAL_PAYMENT_NOT_CONFIRMED"))
                .andExpect(jsonPath("$.message").value("Le montant saisi (6000) est inférieur au solde restant dû (10000.00). "
                        + "Si c'est un paiement partiel intentionnel, renvoyez avec confirmPartial=true."));

        // renvoi avec confirmPartial=true : enregistre
        postJson("/api/paiements-enfants", mensualite(7, 6000, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPartial").value(true))
                .andExpect(jsonPath("$.balanceAfter").value(4000.0));

        // montant superieur au restant : erreur, aucune confirmation possible
        postJson("/api/paiements-enfants", mensualite(7, 5000, false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("AMOUNT_MISMATCH"))
                .andExpect(jsonPath("$.message").value("Le montant saisi (5000) dépasse le solde restant dû (4000.00). "
                        + "Aucun dépassement n'est autorisé."));

        // montant egal au restant : solde la mensualite
        postJson("/api/paiements-enfants", mensualite(7, 4000, false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPartial").value(false))
                .andExpect(jsonPath("$.balanceAfter").value(0.0));

        // mensualite soldee
        postJson("/api/paiements-enfants", mensualite(7, 1000, true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("AMOUNT_MISMATCH"))
                .andExpect(jsonPath("$.message").value("La mensualité de 10/2026 est déjà entièrement payée pour cet enfant"));
    }

    @Test
    void montantDuFige_unChangementDeTarifNeReecritPasLeMois() throws Exception {
        stubEnfant(enfant(7, "Moussa", "Ndiaye", 15000, 10000));
        postJson("/api/paiements-enfants", mensualite(7, 6000, true)).andExpect(status().isCreated());

        stubEnfant(enfant(7, "Moussa", "Ndiaye", 15000, 12000));
        postJson("/api/paiements-enfants", mensualite(7, 4000, false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expectedAmount").value(10000.0))
                .andExpect(jsonPath("$.balanceAfter").value(0.0));
    }

    @Test
    void mensualiteSansMois_400() throws Exception {
        stubEnfant(enfant(7, "Moussa", "Ndiaye", 15000, 10000));
        postJson("/api/paiements-enfants", java.util.Map.of("enfantId", 7, "paymentType", "MENSUALITE",
                "paymentMethod", "ESPECES", "amount", 10000, "concernedYear", 2026))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("L'année et le mois concernés sont requis pour une mensualité"));
    }

    @Test
    void validations400() throws Exception {
        postJson("/api/paiements-enfants", java.util.Map.of("enfantId", 7, "paymentType", "MENSUALITE",
                "paymentMethod", "CHEQUE", "amount", 10000)).andExpect(status().isBadRequest());
        postJson("/api/paiements-enfants", java.util.Map.of("enfantId", 7, "paymentType", "MENSUALITE",
                "paymentMethod", "ESPECES", "amount", 0)).andExpect(status().isBadRequest());
        postJson("/api/paiements-enfants", java.util.Map.of("enfantId", 7, "paymentType", "MENSUALITE",
                "paymentMethod", "ESPECES", "amount", 100, "concernedYear", 2026, "concernedMonth", 13))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- inscriptions

    @Test
    void inscription_montantExactPuisDoublonPuisAnnulationEtNouvelleInscription() throws Exception {
        stubEnfant(enfant(7, "Moussa", "Ndiaye", 15000, 10000));

        postJson("/api/paiements-enfants", inscription(7, 10000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("AMOUNT_MISMATCH"))
                .andExpect(jsonPath("$.message").value("Le montant des frais d'inscription doit être exactement 15000.00. "
                        + "Aucune dérogation n'est autorisée."));

        JsonNode p = json(postJson("/api/paiements-enfants", inscription(7, 15000)).andExpect(status().isCreated()));
        assertThat(fieldNames(p)).isEqualTo(TS_PAIEMENT_ENFANT);
        assertThat(p.get("concernedMonth").isNull()).isTrue();
        assertThat(p.get("balanceAfter").isNull()).isTrue();
        assertThat(p.get("isPartial").isNull()).isTrue();
        assertThat(p.get("expectedAmount").isNull()).isTrue();

        postJson("/api/paiements-enfants", inscription(7, 15000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PAYMENT_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Les frais d'inscription ont déjà été enregistrés pour cet enfant"));

        cancel(p.get("id").asLong()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ANNULE"));
        postJson("/api/paiements-enfants", inscription(7, 15000)).andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- annulation, recu, impression, historique, audit

    @Test
    void annulationRecuImpressionEtAudit() throws Exception {
        stubEnfant(enfant(7, "Moussa", "Ndiaye", 15000, 10000));
        long partiel = json(postJson("/api/paiements-enfants", mensualite(7, 6000, true))).get("id").asLong();
        long inscription = json(postJson("/api/paiements-enfants", inscription(7, 15000))).get("id").asLong();

        JsonNode recu = json(mvc.perform(get("/api/paiements-enfants/" + partiel + "/receipt-data")
                .header("Authorization", GESTIONNAIRE)).andExpect(status().isOk()));
        assertThat(fieldNames(recu)).isEqualTo(TS_RECU_PAIEMENT);
        assertThat(recu.get("isPartial").asBoolean()).isTrue();
        assertThat(recu.get("libelleStatut").asText()).isEqualTo("Paiement partiel");
        assertThat(recu.get("guardianName").asText()).isEqualTo("Tuteur de Moussa");
        assertThat(recu.get("balanceAfter").decimalValue()).isEqualByComparingTo("4000");

        JsonNode recuInscription = json(mvc.perform(get("/api/paiements-enfants/" + inscription + "/receipt-data")
                .header("Authorization", GESTIONNAIRE)));
        assertThat(recuInscription.get("libelleStatut").asText()).isEqualTo("Frais d'inscription");
        assertThat(recuInscription.get("isPartial").asBoolean()).isFalse();
        assertThat(recuInscription.get("concernedMonth").isNull()).isTrue();

        mvc.perform(post("/api/paiements-enfants/" + partiel + "/print-receipt").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.message").value("Impression du reçu enregistrée pour le paiement de Moussa Ndiaye"));

        cancel(partiel).andExpect(status().isOk());
        cancel(partiel).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Ce paiement est déjà annulé"));
        cancel(999999).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Paiement introuvable : 999999"));

        mvc.perform(get("/api/paiements-enfants/enfant/7/mensualites").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("ANNULE"));
        mvc.perform(get("/api/paiements-enfants/enfant/7/inscription").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].paymentType").value("INSCRIPTION"));

        List<JsonNode> audit = drainAudit();
        assertThat(audit).extracting(e -> e.get("action").asText())
                .containsExactlyInAnyOrder("CREATE", "CREATE", "PRINT_RECEIPT", "CANCEL");
        assertThat(audit).allSatisfy(e -> assertThat(e.get("entityType").asText()).isEqualTo("PaiementEnfant"));
    }

    // ---------------------------------------------------------------- suivis

    @Test
    void suiviMensuelEtInscriptions_contratsEtStatuts() throws Exception {
        stubEnfant(enfant(1, "Moussa", "Ndiaye", 15000, 10000));
        stubEnfant(enfant(2, "Awa", "Fall", 15000, 10000));
        stubEnfantsSuivis(List.of(enfant(1, "Moussa", "Ndiaye", 15000, 10000), enfant(2, "Awa", "Fall", 15000, 10000),
                enfant(3, "Ibou", "Sow", 15000, 10000)));
        postJson("/api/paiements-enfants", mensualite(1, 10000, false));
        postJson("/api/paiements-enfants", mensualite(2, 6000, true));
        postJson("/api/paiements-enfants", inscription(1, 15000));

        JsonNode suivi = json(mvc.perform(get("/api/paiements-enfants/mensualites?year=2026&month=10&status=ALL")
                .header("Authorization", GESTIONNAIRE)).andExpect(status().isOk()));
        assertThat(suivi).hasSize(3);
        assertThat(fieldNames(suivi.get(0))).isEqualTo(TS_SUIVI_MENSUEL);
        assertThat(suivi.findValuesAsText("fullName")).containsExactly("Awa Fall", "Ibou Sow", "Moussa Ndiaye");
        assertThat(suivi.findValuesAsText("statutPaiement")).containsExactly("PARTIEL", "IMPAYE", "PAYE");
        assertThat(suivi.get(0).get("montantRestant").decimalValue()).isEqualByComparingTo("4000");
        assertThat(suivi.get(0).get("paiementIds")).hasSize(1);

        mvc.perform(get("/api/paiements-enfants/mensualites?year=2026&month=10&status=impaye").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Ibou Sow"));

        JsonNode inscriptions = json(mvc.perform(get("/api/paiements-enfants/inscriptions?status=ALL")
                .header("Authorization", GESTIONNAIRE)).andExpect(status().isOk()));
        assertThat(fieldNames(inscriptions.get(0))).isEqualTo(TS_INSCRIPTION_SUIVI);
        assertThat(inscriptions.findValuesAsText("statut")).containsExactly("IMPAYE", "IMPAYE", "PAYE");
        assertThat(inscriptions.get(0).get("paymentDate").isNull()).isTrue();
        assertThat(inscriptions.get(0).get("paiementId").isNull()).isTrue();
        assertThat(inscriptions.get(2).get("paymentDate").asText()).isEqualTo("2026-09-28");
    }

    // ---------------------------------------------------------------- appels a enfant-service

    @Test
    void appelAEnfantService_jetonCleInterneEtCorrelationRelayes() throws Exception {
        stubEnfant(enfant(7, "Moussa", "Ndiaye", 15000, 10000));
        mvc.perform(post("/api/paiements-enfants").header("Authorization", GESTIONNAIRE).header("X-Request-Id", "req-paiement-1")
                .contentType("application/json").content(objectMapper.writeValueAsString(mensualite(7, 10000, false))))
                .andExpect(status().isCreated());

        ENFANT_SERVICE.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/internal/enfants/7"))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.equalTo(GESTIONNAIRE))
                .withHeader("X-Internal-Key", com.github.tomakehurst.wiremock.client.WireMock.equalTo(INTERNAL_KEY))
                .withHeader("X-Request-Id", com.github.tomakehurst.wiremock.client.WireMock.equalTo("req-paiement-1")));
    }

    // ---------------------------------------------------------------- verrou de mensualite

    @Test
    void paiementsSimultanes_leVerrouSerialiseEtEmpecheLeDepassement() throws Exception {
        stubEnfant(enfant(7, "Moussa", "Ndiaye", 15000, 10000));

        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            // une autre transaction detient le verrou de la mensualite d'octobre de l'eleve 7
            other.createStatement().execute("SELECT pg_advisory_xact_lock(7, 202610)");

            CompletableFuture<ResultActions> pending = CompletableFuture.supplyAsync(() -> {
                try {
                    return postJson("/api/paiements-enfants", mensualite(7, 6000, true));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });

            // la requete attend le verrou
            await().atMost(Duration.ofSeconds(10)).until(() -> waitingAdvisoryLocks() == 1);

            // pendant ce temps, l'autre transaction enregistre 6000 puis libere le verrou par son commit
            try (PreparedStatement insert = other.prepareStatement("INSERT INTO paiements_enfants (enfant_id, enfant_matricule, "
                    + "enfant_full_name, guardian_name, registered_by_user_id, registered_by_username, payment_type, "
                    + "payment_method, amount, concerned_month, expected_amount, balance_after, is_partial) VALUES "
                    + "(7, 'ENF-2026-0007', 'Moussa Ndiaye', 'T', 1, 'admin', 'MENSUALITE', 'ESPECES', 6000, '2026-10-01', "
                    + "10000, 4000, TRUE)")) {
                insert.executeUpdate();
            }
            other.commit();

            // la requete lit le solde a jour : 6000 depasse le restant de 4000
            pending.get(10, TimeUnit.SECONDS)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("AMOUNT_MISMATCH"))
                    .andExpect(jsonPath("$.message").value("Le montant saisi (6000) dépasse le solde restant dû (4000.00). "
                            + "Aucun dépassement n'est autorisé."));
        }
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM paiements_enfants WHERE enfant_id = 7", Integer.class))
                .isEqualTo(6000);
    }

    @Test
    void verrouNonObtenuDansLeDelai_503EtJamais409() throws Exception {
        stubEnfant(enfant(7, "Moussa", "Ndiaye", 15000, 10000));

        try (Connection other = dataSource.getConnection()) {
            other.setAutoCommit(false);
            other.createStatement().execute("SELECT pg_advisory_xact_lock(7, 202610)");

            postJson("/api/paiements-enfants", mensualite(7, 10000, false))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
            other.rollback();
        }
        // verrou libere : le meme paiement passe
        postJson("/api/paiements-enfants", mensualite(7, 10000, false)).andExpect(status().isCreated());
    }

    private Integer waitingAdvisoryLocks() {
        return jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted", Integer.class);
    }

    private ResultActions cancel(long id) throws Exception {
        return mvc.perform(patch("/api/paiements-enfants/" + id + "/cancel").header("Authorization", GESTIONNAIRE));
    }
}
