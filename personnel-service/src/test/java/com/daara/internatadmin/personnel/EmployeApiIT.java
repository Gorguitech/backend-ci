package com.daara.internatadmin.personnel;

import com.daara.internatadmin.common.audit.AuditMessaging;
import com.daara.internatadmin.common.security.InternalApiKeyFilter;
import com.daara.internatadmin.common.support.InternatAdminContainers;
import com.daara.internatadmin.common.support.TestJwts;
import com.daara.internatadmin.common.support.TestMessages;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.daara.internatadmin.common.support.TestMessages.fieldNames;
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
class EmployeApiIT {

    private static final String INTERNAL_KEY = "cle-interne-de-test";
    private static final String GESTIONNAIRE = "Bearer " + TestJwts.token(5, "awa.diop", "GESTIONNAIRE");

    /** Champs de l'interface Employe de frontend-ref/src/app/core/models/employe.model.ts. */
    private static final Set<String> FRONTEND_EMPLOYE_FIELDS = new TreeSet<>(Set.of("id", "matricule", "firstName",
            "lastName", "fullName", "phone", "function", "contractType", "baseSalary", "hireDate", "status"));

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
        jdbc.update("DELETE FROM employes");
        amqpAdmin.purgeQueue(AuditMessaging.QUEUE, false);
    }

    @Test
    void creation_reponseConformeAuModeleEmployeDuFrontend() throws Exception {
        JsonNode created = json(create(body("Serigne", "Mbaye", "776665544", "2026-09-01"), false)
                .andExpect(status().isCreated()));

        assertThat(fieldNames(created)).isEqualTo(FRONTEND_EMPLOYE_FIELDS);
        assertThat(created.get("id").isIntegralNumber()).isTrue();
        assertThat(created.get("matricule").asText()).isEqualTo("EMP-2026-0001");
        assertThat(created.get("fullName").asText()).isEqualTo("Serigne Mbaye");
        assertThat(created.get("function").asText()).isEqualTo("MAITRE_CORANIQUE");
        assertThat(created.get("contractType").asText()).isEqualTo("TEMPS_PLEIN");
        assertThat(created.get("baseSalary").isNumber()).isTrue();
        assertThat(created.get("baseSalary").decimalValue()).isEqualByComparingTo("75000");
        assertThat(created.get("hireDate").asText()).isEqualTo("2026-09-01");
        assertThat(created.get("status").asText()).isEqualTo("ACTIF");
    }

    @Test
    void lecture_toutesLesRoutes_memeModele() throws Exception {
        create(body("Serigne", "Mbaye", "776665544", "2026-09-01"), false);

        for (String path : List.of("/api/employes", "/api/employes/search?q=mba", "/api/employes?status=ACTIF")) {
            JsonNode list = json(mvc.perform(get(path).header("Authorization", GESTIONNAIRE)).andExpect(status().isOk()));
            assertThat(list).hasSize(1);
            assertThat(fieldNames(list.get(0))).as(path).isEqualTo(FRONTEND_EMPLOYE_FIELDS);
        }
        JsonNode byMatricule = json(mvc.perform(get("/api/employes/matricule/EMP-2026-0001")
                .header("Authorization", GESTIONNAIRE)).andExpect(status().isOk()));
        assertThat(fieldNames(byMatricule)).isEqualTo(FRONTEND_EMPLOYE_FIELDS);
    }

    @Test
    void matricule_sequenceParAnneeEtCollisionReelle() throws Exception {
        create(body("A", "Un", "770000001", "2026-01-10"), false).andExpect(jsonPath("$.matricule").value("EMP-2026-0001"));
        create(body("B", "Deux", "770000002", "2025-10-01"), false).andExpect(jsonPath("$.matricule").value("EMP-2025-0001"));
        jdbc.update("INSERT INTO employes (matricule, last_name, first_name, phone, role_function, contract_type, "
                + "base_salary, hire_date) VALUES ('EMP-2026-0003', 'Hors', 'Application', '770000099', 'GARDIEN', "
                + "'PARTIEL', 1, '2026-01-01')");

        create(body("C", "Trois", "770000003", "2026-02-10"), false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matricule").value("EMP-2026-0004"));
    }

    @Test
    void doublon_409PossibleDuplicateAvecLeMessageDuMonolithe_puisConfirmation() throws Exception {
        create(body("Serigne", "Mbaye", "776665544", "2026-09-01"), false).andExpect(status().isCreated());

        create(body("serigne", "MBAYE", "776665544", "2026-09-01"), false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("POSSIBLE_DUPLICATE"))
                .andExpect(jsonPath("$.message").value("Un employé avec le même nom, prénom et téléphone existe déjà. "));

        create(body("serigne", "MBAYE", "776665544", "2026-09-01"), true).andExpect(status().isCreated());
        create(body("Serigne", "Mbaye", "770000000", "2026-09-01"), false).andExpect(status().isCreated());
    }

    @Test
    void creation_validations400() throws Exception {
        for (Map.Entry<String, Object> invalid : Map.<String, Object>of(
                "function", "DIRECTEUR", "contractType", "INTERIM", "baseSalary", 0, "phone", "", "lastName", "").entrySet()) {
            Map<String, Object> body = body("Serigne", "Mbaye", "776665544", "2026-09-01");
            body.put(invalid.getKey(), invalid.getValue());
            create(body, false).andExpect(status().isBadRequest());
        }
        Map<String, Object> withoutHireDate = body("Serigne", "Mbaye", "776665544", "2026-09-01");
        withoutHireDate.remove("hireDate");
        create(withoutHireDate, false).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employes", Integer.class)).isZero();
    }

    @Test
    void contraintesEnBase_fonctionEtContratControles() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO employes (matricule, last_name, first_name, phone, role_function, "
                + "contract_type, base_salary, hire_date) VALUES ('EMP-2026-0099', 'X', 'Y', '7', 'DIRECTEUR', 'PARTIEL', "
                + "1, '2026-01-01')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lecture_filtresRechercheEtIntrouvables() throws Exception {
        long serigne = id(create(body("Serigne", "Mbaye", "776665544", "2026-09-01"), false));
        create(body("Fatou", "Gueye", "772222222", "2026-09-01"), false);
        setStatus(serigne, "EN_CONGE");

        mvc.perform(get("/api/employes").header("Authorization", GESTIONNAIRE)).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/employes?status=EN_CONGE").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$[*].firstName", containsInAnyOrder("Serigne")));
        mvc.perform(get("/api/employes/search?q=GUE").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$[*].firstName", containsInAnyOrder("Fatou")));
        mvc.perform(get("/api/employes?status=PARTI").header("Authorization", GESTIONNAIRE)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/employes/999999").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Employé introuvable : 999999"));
        mvc.perform(get("/api/employes/matricule/EMP-1999-0001").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Aucun employé trouvé avec le matricule : EMP-1999-0001"));
    }

    @Test
    void modification_statut_impressions_toutesAuditees() throws Exception {
        long id = id(create(body("Serigne", "Mbaye", "776665544", "2026-09-01"), false));

        Map<String, Object> update = body("Serigne", "Ndiaye", "776665544", "2026-09-01");
        update.remove("hireDate");
        update.put("baseSalary", 90000);
        update.put("contractType", "PARTIEL");
        mvc.perform(put("/api/employes/" + id).header("Authorization", GESTIONNAIRE)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Serigne Ndiaye"))
                .andExpect(jsonPath("$.baseSalary").value(90000))
                .andExpect(jsonPath("$.contractType").value("PARTIEL"))
                .andExpect(jsonPath("$.hireDate").value("2026-09-01"));

        mvc.perform(patch("/api/employes/" + id + "/status").header("Authorization", GESTIONNAIRE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ANCIEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANCIEN"));
        mvc.perform(patch("/api/employes/" + id + "/status").header("Authorization", GESTIONNAIRE)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());

        mvc.perform(post("/api/employes/" + id + "/print-card").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.message").value("Impression de la carte enregistrée pour Serigne Ndiaye"));
        mvc.perform(post("/api/employes/" + id + "/print-certificate").header("Authorization", GESTIONNAIRE))
                .andExpect(jsonPath("$.message").value("Impression du certificat enregistrée pour Serigne Ndiaye"));

        List<JsonNode> audit = TestMessages.drainAudit(rabbitTemplate, objectMapper);
        assertThat(audit).extracting(e -> e.get("action").asText())
                .containsExactlyInAnyOrder("CREATE", "UPDATE", "UPDATE_STATUS", "PRINT_CARD", "PRINT_CERTIFICATE");
        assertThat(audit).allSatisfy(e -> {
            assertThat(e.get("entityType").asText()).isEqualTo("Employe");
            assertThat(e.get("entityId").asLong()).isEqualTo(id);
            assertThat(e.get("username").asText()).isEqualTo("awa.diop");
        });
    }

    @Test
    void securite_sansJeton403_jetonExpire401() throws Exception {
        mvc.perform(get("/api/employes")).andExpect(status().isForbidden());
        String expired = TestJwts.token(5, "awa.diop", "GESTIONNAIRE", Instant.now().minusSeconds(3600));
        mvc.perform(get("/api/employes").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void interne_ficheListeParStatutsEtEffectifs() throws Exception {
        long serigne = id(create(body("Serigne", "Mbaye", "776665544", "2026-09-01"), false));
        long fatou = id(create(body("Fatou", "Gueye", "772222222", "2026-09-01"), false));
        long modou = id(create(body("Modou", "Sarr", "773333333", "2025-09-01"), false));
        setStatus(fatou, "EN_CONGE");
        setStatus(modou, "ANCIEN");

        internal("/internal/employes/" + serigne).andExpect(status().isOk())
                .andExpect(jsonPath("$.baseSalary").value(75000))
                .andExpect(jsonPath("$.function").value("MAITRE_CORANIQUE"));
        internal("/internal/employes?statuses=ACTIF,EN_CONGE").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].firstName", containsInAnyOrder("Serigne", "Fatou")));
        internal("/internal/employes/counts").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActifs").value(1))
                .andExpect(jsonPath("$.totalEnConge").value(1))
                .andExpect(jsonPath("$.totalAnciens").value(1));
        mvc.perform(get("/internal/employes/" + serigne).header("Authorization", GESTIONNAIRE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("INTERNAL_ACCESS_DENIED"));
    }

    @Test
    void sante_etDocumentationOpenApi() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/employes'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/employes'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/employes'].get").exists())
                .andExpect(jsonPath("$.paths['/api/employes/search'].get").exists())
                .andExpect(jsonPath("$.paths['/api/employes/matricule/{matricule}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/employes/{id}'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/employes/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/employes/{id}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/employes/{id}/print-card'].post").exists())
                .andExpect(jsonPath("$.paths['/api/employes/{id}/print-certificate'].post").exists())
                .andExpect(jsonPath("$.paths['/internal/employes/{id}']").doesNotExist());
    }

    private Map<String, Object> body(String firstName, String lastName, String phone, String hireDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("phone", phone);
        body.put("function", "MAITRE_CORANIQUE");
        body.put("contractType", "TEMPS_PLEIN");
        body.put("baseSalary", 75000);
        body.put("hireDate", hireDate);
        return body;
    }

    private ResultActions create(Map<String, Object> body, boolean confirmDuplicate) throws Exception {
        return mvc.perform(post("/api/employes").param("confirmDuplicate", String.valueOf(confirmDuplicate))
                .header("Authorization", GESTIONNAIRE).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions internal(String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", GESTIONNAIRE).header(InternalApiKeyFilter.HEADER, INTERNAL_KEY));
    }

    private void setStatus(long id, String status) throws Exception {
        mvc.perform(patch("/api/employes/" + id + "/status").header("Authorization", GESTIONNAIRE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}")).andExpect(status().isOk());
    }

    private long id(ResultActions result) throws Exception {
        return json(result).get("id").asLong();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
