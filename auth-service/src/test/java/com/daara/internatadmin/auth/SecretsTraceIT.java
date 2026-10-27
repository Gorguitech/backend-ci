package com.daara.internatadmin.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H9 : aucun mot de passe (temporaire, initial, nouveau), code de recuperation ni jeton n'apparait
 * dans les journaux ni dans les evenements d'audit, sur l'ensemble des parcours sensibles.
 */
@ExtendWith(OutputCaptureExtension.class)
class SecretsTraceIT extends AbstractIntegrationTest {

    @Test
    void aucunSecretDansLesJournauxNiDansLAudit(CapturedOutput output) throws Exception {
        List<String> secrets = new ArrayList<>();
        secrets.add(ADMIN_INITIAL_PASSWORD);

        JsonNode adminLogin = login("admin", ADMIN_INITIAL_PASSWORD);
        String admin = bearer(adminLogin);
        secrets.add(adminLogin.get("token").asText());

        JsonNode created = json(mvc.perform(post("/api/users").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Awa\",\"lastName\":\"Diop\"}"))
                .andExpect(status().isCreated()).andReturn());
        secrets.add(created.get("temporaryPassword").asText());
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE username = 'awa.diop'", Long.class);

        JsonNode reset = json(mvc.perform(post("/api/users/" + id + "/reset-password").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn());
        secrets.add(reset.get("temporaryPassword").asText());

        JsonNode userLogin = login("awa.diop", reset.get("temporaryPassword").asText());
        secrets.add(userLogin.get("token").asText());
        mvc.perform(put("/api/users/me/password").header("Authorization", bearer(userLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", reset.get("temporaryPassword").asText(), "newPassword", "Gestion-Mdp-2026"))))
                .andExpect(status().isOk());
        secrets.add("Gestion-Mdp-2026");

        JsonNode changed = json(mvc.perform(put("/api/users/me/password").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", ADMIN_INITIAL_PASSWORD, "newPassword", "Admin-Mdp-2026"))))
                .andExpect(status().isOk()).andReturn());
        secrets.add("Admin-Mdp-2026");
        secrets.add(changed.get("recoveryCode").asText());

        JsonNode recovered = json(mvc.perform(post("/api/auth/reset-with-recovery-code").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin",
                                "recoveryCode", changed.get("recoveryCode").asText(), "newPassword", "Admin-Retrouve-2026"))))
                .andExpect(status().isOk()).andReturn());
        secrets.add("Admin-Retrouve-2026");
        secrets.add(recovered.get("newRecoveryCode").asText());

        // actions auditees, pour que la file contienne des evenements a inspecter
        mvc.perform(post("/api/users/" + id + "/deactivate").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(post("/api/users/" + id + "/activate").header("Authorization", admin)).andExpect(status().isOk());

        // tentatives echouees : le mot de passe essaye ne doit pas non plus etre journalise
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginBody("admin", "Essai-Rate-2026"))));
        secrets.add("Essai-Rate-2026");

        List<JsonNode> audit = drainAuditMessages();
        assertThat(audit).extracting(n -> n.get("action").asText()).containsExactly("DEACTIVATE", "ACTIVATE");

        String auditText = audit.toString();
        String logs = output.getAll();
        for (String secret : secrets) {
            assertThat(auditText).as("secret absent de l'audit").doesNotContain(secret);
            assertThat(logs).as("secret absent des journaux").doesNotContain(secret);
        }
        assertThat(logs).doesNotContain("$2a$").doesNotContain("$2y$").doesNotContain("$2b$");
    }
}
