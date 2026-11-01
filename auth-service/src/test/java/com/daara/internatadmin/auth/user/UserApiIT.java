package com.daara.internatadmin.auth.user;

import com.daara.internatadmin.auth.AbstractIntegrationTest;
import com.daara.internatadmin.common.security.InternalApiKeyFilter;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiIT extends AbstractIntegrationTest {

    @Test
    void creationDeGestionnaire_puisConnexionAvecLeMotDePasseTemporaire() throws Exception {
        JsonNode created = json(create(adminBearer(), "Awa", "Diop").andExpect(status().isCreated()).andReturn());

        assertThat(created.get("username").asText()).isEqualTo("awa.diop");
        assertThat(created.get("fullName").asText()).isEqualTo("Awa Diop");
        assertThat(created.get("temporaryPassword").asText()).hasSize(10);

        JsonNode login = login("awa.diop", created.get("temporaryPassword").asText());
        assertThat(login.get("role").asText()).isEqualTo("GESTIONNAIRE");
        assertThat(login.get("mustChangePassword").asBoolean()).isTrue();
    }

    @Test
    void homonymes_suffixeNumeriqueApresCollisionReelleEnBase() throws Exception {
        String admin = adminBearer();
        create(admin, "Awa", "Diop").andExpect(status().isCreated());
        // "Àwa" est normalise en "awa" : la premiere tentative heurte la contrainte UNIQUE en base
        create(admin, "Àwa", "Diop").andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("awa.diop2"));
        create(admin, "Awa", "Diop").andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("awa.diop3"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE username LIKE 'awa.diop%'", Integer.class)).isEqualTo(3);
    }

    @Test
    void routesAdmin_interditesAuGestionnaire() throws Exception {
        String gestionnaire = gestionnaireBearer();
        create(gestionnaire, "X", "Y").andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", gestionnaire)).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/stats").header("Authorization", gestionnaire)).andExpect(status().isForbidden());
        mvc.perform(post("/api/users/1/deactivate").header("Authorization", gestionnaire)).andExpect(status().isForbidden());
        mvc.perform(get("/api/users")).andExpect(status().isForbidden());
    }

    @Test
    void listeEtStatistiques() throws Exception {
        String admin = adminBearer();
        create(admin, "Awa", "Diop");
        create(admin, "Moussa", "Sow");

        mvc.perform(get("/api/users").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.username == 'awa.diop')].fullName").value("Awa Diop"))
                .andExpect(jsonPath("$[?(@.username == 'awa.diop')].role").value("GESTIONNAIRE"))
                .andExpect(jsonPath("$[?(@.username == 'awa.diop')].active").value(true))
                .andExpect(jsonPath("$[?(@.username == 'awa.diop')].mustChangePassword").value(true));

        jdbc.update("UPDATE users SET active = FALSE WHERE username = 'moussa.sow'");
        mvc.perform(get("/api/users/stats").header("Authorization", admin))
                .andExpect(jsonPath("$.totalUtilisateurs").value(3))
                .andExpect(jsonPath("$.totalActifs").value(2))
                .andExpect(jsonPath("$.totalDesactives").value(1))
                .andExpect(jsonPath("$.totalAdmins").value(1))
                .andExpect(jsonPath("$.totalGestionnaires").value(2))
                .andExpect(jsonPath("$.comptesEnAttenteChangementMdp").value(3));
    }

    @Test
    void reinitialisationDuMotDePasse() throws Exception {
        String admin = adminBearer();
        JsonNode created = json(create(admin, "Awa", "Diop").andReturn());
        Long id = userId("awa.diop");

        JsonNode reset = json(mvc.perform(post("/api/users/" + id + "/reset-password").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(reset.get("username").asText()).isEqualTo("awa.diop");
        assertThat(reset.get("temporaryPassword").asText()).hasSize(10).isNotEqualTo(created.get("temporaryPassword").asText());
        assertThat(login("awa.diop", reset.get("temporaryPassword").asText()).get("mustChangePassword").asBoolean()).isTrue();

        mvc.perform(post("/api/users/999999/reset-password").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Utilisateur introuvable : 999999"));
    }

    @Test
    void desactivation_auditeeEtEffectiveAuLogin_puisReactivation() throws Exception {
        String admin = adminBearer();
        JsonNode created = json(create(admin, "Awa", "Diop").andReturn());
        Long id = userId("awa.diop");
        drainAuditMessages();

        mvc.perform(post("/api/users/" + adminId() + "/deactivate").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vous ne pouvez pas désactiver votre propre compte"));

        mvc.perform(post("/api/users/" + id + "/deactivate").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        List<JsonNode> audit = drainAuditMessages();
        assertThat(audit).hasSize(1);
        assertThat(audit.getFirst().get("action").asText()).isEqualTo("DEACTIVATE");
        assertThat(audit.getFirst().get("entityType").asText()).isEqualTo("User");
        assertThat(audit.getFirst().get("entityId").asLong()).isEqualTo(id);
        assertThat(audit.getFirst().get("username").asText()).isEqualTo("admin");
        assertThat(audit.getFirst().get("userId").asLong()).isEqualTo(adminId());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginBody("awa.diop", created.get("temporaryPassword").asText()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));

        mvc.perform(post("/api/users/" + id + "/activate").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        login("awa.diop", created.get("temporaryPassword").asText());
    }

    @Test
    void modification() throws Exception {
        String admin = adminBearer();
        create(admin, "Awa", "Diop");
        Long id = userId("awa.diop");
        drainAuditMessages();

        mvc.perform(put("/api/users/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Awa\",\"lastName\":\"Ndiaye\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Awa Ndiaye"))
                .andExpect(jsonPath("$.username").value("awa.diop"));
        mvc.perform(put("/api/users/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"lastName\":\"Ndiaye\"}"))
                .andExpect(status().isBadRequest());

        List<JsonNode> audit = drainAuditMessages();
        assertThat(audit).extracting(n -> n.get("action").asText()).containsExactly("UPDATE");
        assertThat(audit.getFirst().get("entityType").asText()).isEqualTo("User");
        assertThat(audit.getFirst().get("entityId").asLong()).isEqualTo(id);
    }

    @Test
    void endpointsInternes_etatDuCompteEtStatistiques() throws Exception {
        String admin = adminBearer();
        create(admin, "Awa", "Diop");
        Long id = userId("awa.diop");
        jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", id);

        mvc.perform(get("/internal/users/" + id + "/state").header("Authorization", admin)
                        .header(InternalApiKeyFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.role").value("GESTIONNAIRE"));
        mvc.perform(get("/internal/users/stats").header("Authorization", admin).header(InternalApiKeyFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDesactives").value(1));
        mvc.perform(get("/internal/users/" + id + "/state").header("Authorization", admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("INTERNAL_ACCESS_DENIED"));
        mvc.perform(get("/internal/users/999999/state").header("Authorization", admin)
                        .header(InternalApiKeyFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    private ResultActions create(String bearer, String firstName, String lastName) throws Exception {
        return mvc.perform(post("/api/users").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("firstName", firstName, "lastName", lastName))));
    }

    private Long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private String gestionnaireBearer() throws Exception {
        JsonNode created = json(create(adminBearer(), "Gestion", "Naire").andReturn());
        return bearer(login("gestion.naire", created.get("temporaryPassword").asText()));
    }
}
