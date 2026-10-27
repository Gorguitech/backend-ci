package com.daara.internatadmin.enfant;

import com.daara.internatadmin.common.audit.AuditMessaging;
import com.daara.internatadmin.common.security.InternalApiKeyFilter;
import com.daara.internatadmin.common.support.InternatAdminContainers;
import com.daara.internatadmin.common.support.TestJwts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(InternatAdminContainers.class)
class EnfantApiIT {

    private static final String INTERNAL_KEY = "cle-interne-de-test";
    private static final String GESTIONNAIRE = "Bearer " + TestJwts.token(5, "awa.diop", "GESTIONNAIRE");

    /** Champs de l'interface Enfant de frontend-ref/src/app/core/models/enfant.model.ts. */
    private static final Set<String> FRONTEND_ENFANT_FIELDS = Set.of("id", "matricule", "firstName", "lastName",
            "fullName", "birthDate", "approximateAge", "gender", "guardianName", "guardianPhone", "neighborhood",
            "entryDate", "status", "roomGroup", "registrationFee", "monthlyFee");

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

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("internatadmin.security.jwt-public-key-b64", TestJwts::publicKeyB64);
        registry.add("internatadmin.security.internal-api-key", () -> INTERNAL_KEY);
    }

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM enfants");
        amqpAdmin.purgeQueue(AuditMessaging.QUEUE, false);
    }

    // ---------------------------------------------------------------- contrat avec le frontend

    @Test
    void creation_reponseConformeAuModeleEnfantDuFrontend() throws Exception {
        JsonNode created = json(create(body("Moussa", "Ndiaye", "771234567", "2026-09-01"), false)
                .andExpect(status().isCreated()));

        assertThat(fieldNames(created)).isEqualTo(new TreeSet<>(FRONTEND_ENFANT_FIELDS));
        assertThat(created.get("id").isIntegralNumber()).isTrue();
        assertThat(created.get("matricule").asText()).isEqualTo("ENF-2026-0001");
        assertThat(created.get("fullName").asText()).isEqualTo("Moussa Ndiaye");
        assertThat(created.get("birthDate").asText()).isEqualTo("2018-03-02");
        assertThat(created.get("approximateAge").isNull()).as("null quand seule la date est connue").isTrue();
        assertThat(created.get("gender").asText()).isEqualTo("M");
        assertThat(created.get("entryDate").asText()).isEqualTo("2026-09-01");
        assertThat(created.get("status").asText()).isEqualTo("ACTIF");
        assertThat(created.get("registrationFee").isNumber()).isTrue();
        assertThat(created.get("registrationFee").decimalValue()).isEqualByComparingTo("15000");
        assertThat(created.get("monthlyFee").isNumber()).isTrue();
        assertThat(created.get("neighborhood").asText()).isEqualTo("Médina");
        assertThat(created.get("roomGroup").asText()).isEqualTo("Groupe A");
    }

    @Test
    void lecture_listeRechercheEtMatricule_memeModele() throws Exception {
        create(body("Moussa", "Ndiaye", "771234567", "2026-09-01"), false);

        for (String path : List.of("/api/enfants", "/api/enfants/search?q=ndia", "/api/enfants?status=ACTIF")) {
            JsonNode list = json(mvc.perform(get(path).header("Authorization", GESTIONNAIRE)).andExpect(status().isOk()));
            assertThat(list).hasSize(1);
            assertThat(fieldNames(list.get(0))).as(path).isEqualTo(new TreeSet<>(FRONTEND_ENFANT_FIELDS));
        }
        JsonNode byMatricule = json(mvc.perform(get("/api/enfants/matricule/ENF-2026-0001")
                .header("Authorization", GESTIONNAIRE)).andExpect(status().isOk()));
        assertThat(fieldNames(byMatricule)).isEqualTo(new TreeSet<>(FRONTEND_ENFANT_FIELDS));
    }

    // ---------------------------------------------------------------- creation

    @Test
    void matricule_sequenceParAnneeDEntree() throws Exception {
        create(body("A", "Un", "770000001", "2026-01-10"), false).andExpect(jsonPath("$.matricule").value("ENF-2026-0001"));
        create(body("B", "Deux", "770000002", "2026-02-10"), false).andExpect(jsonPath("$.matricule").value("ENF-2026-0002"));
        create(body("C", "Trois", "770000003", "2025-10-01"), false).andExpect(jsonPath("$.matricule").value("ENF-2025-0001"));
    }

    @Test
    void matricule_collisionReelleEnBase_sequenceSuivante() throws Exception {
        create(body("A", "Un", "770000001", "2026-01-10"), false);
        // ligne inseree hors application : la sequence calculee (2 lignes -> 0003) est deja prise
        insertDirect("ENF-2026-0003", "Hors", "Application", "770000099");

        create(body("B", "Deux", "770000002", "2026-02-10"), false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matricule").value("ENF-2026-0004"));
    }

    @Test
    void doublon_seul409PossibleDuplicate_puisConfirmation() throws Exception {
        create(body("Moussa", "Ndiaye", "771234567", "2026-09-01"), false).andExpect(status().isCreated());

        create(body("MOUSSA", "ndiaye", "771234567", "2026-09-01"), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("POSSIBLE_DUPLICATE"))
                .andExpect(jsonPath("$.message").value("Un enfant avec le même nom, prénom et téléphone de tuteur existe déjà. "
                        + "Si c'est intentionnel, renvoyez la requête avec confirmDuplicate=true."));

        create(body("MOUSSA", "ndiaye", "771234567", "2026-09-01"), true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matricule").value("ENF-2026-0002"));

        // autre telephone de tuteur : pas un doublon
        create(body("Moussa", "Ndiaye", "770000000", "2026-09-01"), false).andExpect(status().isCreated());
    }

    @Test
    void creation_niDateNiAge_400AvecLeMessageDuMonolithe() throws Exception {
        Map<String, Object> body = body("Moussa", "Ndiaye", "771234567", "2026-09-01");
        body.remove("birthDate");
        create(body, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("La date de naissance ou l'âge approximatif doit être renseigné"));
    }

    @Test
    void creation_validations400() throws Exception {
        for (Map.Entry<String, Object> invalid : Map.<String, Object>of(
                "gender", "X", "approximateAge", 31, "registrationFee", -1, "firstName", "", "guardianPhone", "").entrySet()) {
            Map<String, Object> body = body("Moussa", "Ndiaye", "771234567", "2026-09-01");
            body.put(invalid.getKey(), invalid.getValue());
            create(body, false).andExpect(status().isBadRequest());
        }
        Map<String, Object> withoutEntryDate = body("Moussa", "Ndiaye", "771234567", "2026-09-01");
        withoutEntryDate.remove("entryDate");
        create(withoutEntryDate, false).andExpect(status().isBadRequest());
        assertThat(count()).isZero();
    }

    @Test
    void contrainteEnBase_dateOuAgeObligatoire_doubleSecurite() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO enfants (matricule, last_name, first_name, gender, guardian_name, "
                + "guardian_phone, entry_date, registration_fee, monthly_fee) VALUES ('ENF-2026-0099', 'X', 'Y', 'M', 'T', "
                + "'770000000', '2026-01-01', 1, 1)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_birth_info");
    }

    // ---------------------------------------------------------------- lecture

    @Test
    void lecture_filtresEtRecherche() throws Exception {
        long moussa = id(create(body("Moussa", "Ndiaye", "771234567", "2026-09-01"), false));
        create(body("Awa", "Fall", "772222222", "2026-09-01"), false);
        mvc.perform(patch("/api/enfants/" + moussa + "/status").header("Authorization", GESTIONNAIRE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SORTI\"}"));

        mvc.perform(get("/api/enfants").header("Authorization", GESTIONNAIRE)).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/enfants?status=ACTIF").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$[*].firstName", containsInAnyOrder("Awa")));
        mvc.perform(get("/api/enfants?status=SORTI").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$[*].firstName", containsInAnyOrder("Moussa")));
        mvc.perform(get("/api/enfants/search?q=FAL").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$[*].firstName", containsInAnyOrder("Awa")));
        mvc.perform(get("/api/enfants/search?q=mou").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$[*].firstName", containsInAnyOrder("Moussa")));
        mvc.perform(get("/api/enfants?status=INCONNU").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/enfants/search").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lecture_introuvable_400InvalidRequest() throws Exception {
        mvc.perform(get("/api/enfants/999999").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Enfant introuvable : 999999"));
        mvc.perform(get("/api/enfants/matricule/ENF-1999-0001").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Aucun enfant trouvé avec le matricule : ENF-1999-0001"));
    }

    // ---------------------------------------------------------------- modification, statut, impressions, audit

    @Test
    void modification_puisStatut_puisImpressions_toutesAuditees() throws Exception {
        long id = id(create(body("Moussa", "Ndiaye", "771234567", "2026-09-01"), false));

        Map<String, Object> update = body("Moussa", "Diallo", "771234567", "2026-09-01");
        update.remove("entryDate");
        update.put("monthlyFee", 12000);
        mvc.perform(put("/api/enfants/" + id).header("Authorization", GESTIONNAIRE)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Moussa Diallo"))
                .andExpect(jsonPath("$.monthlyFee").value(12000))
                .andExpect(jsonPath("$.matricule").value("ENF-2026-0001"))
                .andExpect(jsonPath("$.entryDate").value("2026-09-01"));

        mvc.perform(patch("/api/enfants/" + id + "/status").header("Authorization", GESTIONNAIRE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EN_VACANCES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_VACANCES"));

        mvc.perform(post("/api/enfants/" + id + "/print-card").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.message").value("Impression de la carte enregistrée pour Moussa Diallo"))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/enfants/" + id + "/print-certificate").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Impression du certificat enregistrée pour Moussa Diallo"));

        // publication asynchrone : l'ordre d'arrivee dans la file n'est pas garanti
        List<JsonNode> audit = drainAudit();
        assertThat(audit).extracting(e -> e.get("action").asText())
                .containsExactlyInAnyOrder("CREATE", "UPDATE", "UPDATE_STATUS", "PRINT_CARD", "PRINT_CERTIFICATE");
        assertThat(audit).allSatisfy(e -> {
            assertThat(e.get("entityType").asText()).isEqualTo("Enfant");
            assertThat(e.get("entityId").asLong()).isEqualTo(id);
            assertThat(e.get("userId").asLong()).isEqualTo(5L);
            assertThat(e.get("username").asText()).isEqualTo("awa.diop");
        });
    }

    @Test
    void statutAbsentOuInconnu_400_correctionDuMonolithe() throws Exception {
        long id = id(create(body("Moussa", "Ndiaye", "771234567", "2026-09-01"), false));
        mvc.perform(patch("/api/enfants/" + id + "/status").header("Authorization", GESTIONNAIRE)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/enfants/" + id + "/status").header("Authorization", GESTIONNAIRE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PARTI\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void echecDeCreation_aucunEvenementDAudit() throws Exception {
        create(body("Moussa", "Ndiaye", "771234567", "2026-09-01"), false);
        drainAudit();
        create(body("Moussa", "Ndiaye", "771234567", "2026-09-01"), false).andExpect(status().isConflict());
        assertThat(drainAudit()).isEmpty();
    }

    // ---------------------------------------------------------------- securite, interne, documentation

    @Test
    void securite_sansJeton403_jetonExpire401() throws Exception {
        mvc.perform(get("/api/enfants")).andExpect(status().isForbidden());
        String expired = TestJwts.token(5, "awa.diop", "GESTIONNAIRE", Instant.now().minusSeconds(3600));
        mvc.perform(get("/api/enfants").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void interne_ficheListeParStatutsEtEffectifs() throws Exception {
        long moussa = id(create(body("Moussa", "Ndiaye", "771234567", "2026-09-01"), false));
        long awa = id(create(body("Awa", "Fall", "772222222", "2026-08-15"), false));
        long ibou = id(create(body("Ibou", "Sow", "773333333", "2025-09-01"), false));
        setStatus(awa, "EN_VACANCES");
        setStatus(ibou, "SORTI");

        internal("/internal/enfants/" + moussa).andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyFee").value(10000))
                .andExpect(jsonPath("$.registrationFee").value(15000))
                .andExpect(jsonPath("$.guardianName").value("Tuteur de Moussa"));
        internal("/internal/enfants?statuses=ACTIF,EN_VACANCES").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].firstName", containsInAnyOrder("Moussa", "Awa")));
        internal("/internal/enfants/counts?year=2026&month=9").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActifs").value(1))
                .andExpect(jsonPath("$.totalEnVacances").value(1))
                .andExpect(jsonPath("$.totalSortis").value(1))
                .andExpect(jsonPath("$.nouveauxDuMois").value(1));
        internal("/internal/enfants/999999").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));

        mvc.perform(get("/internal/enfants/" + moussa).header("Authorization", GESTIONNAIRE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("INTERNAL_ACCESS_DENIED"));
    }

    @Test
    void sante_etDocumentationOpenApi() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/enfants'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/enfants'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/enfants'].post.parameters[?(@.name == 'confirmDuplicate')]").exists())
                .andExpect(jsonPath("$.paths['/api/enfants'].get").exists())
                .andExpect(jsonPath("$.paths['/api/enfants/search'].get").exists())
                .andExpect(jsonPath("$.paths['/api/enfants/{id}'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/enfants/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/enfants/{id}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/enfants/{id}/print-card'].post").exists())
                .andExpect(jsonPath("$.paths['/api/enfants/{id}/print-certificate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/enfants/matricule/{matricule}'].get").exists())
                .andExpect(jsonPath("$.paths['/internal/enfants/{id}']").doesNotExist());
    }

    // ---------------------------------------------------------------- outils

    private Map<String, Object> body(String firstName, String lastName, String guardianPhone, String entryDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("birthDate", "2018-03-02");
        body.put("gender", "M");
        body.put("guardianName", "Tuteur de " + firstName);
        body.put("guardianPhone", guardianPhone);
        body.put("neighborhood", "Médina");
        body.put("entryDate", entryDate);
        body.put("roomGroup", "Groupe A");
        body.put("registrationFee", 15000);
        body.put("monthlyFee", 10000);
        return body;
    }

    private ResultActions create(Map<String, Object> body, boolean confirmDuplicate) throws Exception {
        return mvc.perform(post("/api/enfants").param("confirmDuplicate", String.valueOf(confirmDuplicate))
                .header("Authorization", GESTIONNAIRE).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions internal(String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", GESTIONNAIRE).header(InternalApiKeyFilter.HEADER, INTERNAL_KEY));
    }

    private void setStatus(long id, String status) throws Exception {
        mvc.perform(patch("/api/enfants/" + id + "/status").header("Authorization", GESTIONNAIRE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}")).andExpect(status().isOk());
    }

    private void insertDirect(String matricule, String firstName, String lastName, String phone) {
        jdbc.update("INSERT INTO enfants (matricule, last_name, first_name, approximate_age, gender, guardian_name, "
                + "guardian_phone, entry_date, registration_fee, monthly_fee) VALUES (?, ?, ?, 8, 'M', 'T', ?, '2026-01-01', 1, 1)",
                matricule, lastName, firstName, phone);
    }

    private long id(ResultActions result) throws Exception {
        return json(result).get("id").asLong();
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM enfants", Integer.class);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private List<JsonNode> drainAudit() throws Exception {
        List<JsonNode> messages = new ArrayList<>();
        Message message;
        while ((message = rabbitTemplate.receive(AuditMessaging.QUEUE, 2000)) != null) {
            messages.add(objectMapper.readTree(message.getBody()));
        }
        return messages;
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        Iterator<String> it = node.fieldNames();
        it.forEachRemaining(names::add);
        return names;
    }
}
