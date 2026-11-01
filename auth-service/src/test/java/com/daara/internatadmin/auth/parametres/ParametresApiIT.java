package com.daara.internatadmin.auth.parametres;

import com.daara.internatadmin.auth.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ParametresApiIT extends AbstractIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 1, 2, 3};

    @Autowired
    TestRestTemplate rest;

    @Test
    void lectureParTousEtModificationParLAdmin() throws Exception {
        String admin = adminBearer();
        mvc.perform(get("/api/parametres").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Daara (à configurer)"))
                .andExpect(jsonPath("$.hasLogo").value(false));

        mvc.perform(put("/api/parametres").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Daara Serigne Touba\",\"address\":\"Touba\",\"phone\":\"771234567\","
                                + "\"email\":\"daara@exemple.sn\",\"directorName\":\"Serigne Mbaye\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Daara Serigne Touba"))
                .andExpect(jsonPath("$.directorName").value("Serigne Mbaye"));

        mvc.perform(put("/api/parametres").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());

        List<JsonNode> audit = drainAuditMessages();
        assertThat(audit).extracting(n -> n.get("action").asText()).containsExactly("UPDATE");
        assertThat(audit.getFirst().get("entityType").asText()).isEqualTo("Parametres");
    }

    @Test
    void gestionnaire_lectureSeulement() throws Exception {
        String gestionnaire = gestionnaireBearer();
        mvc.perform(get("/api/parametres").header("Authorization", gestionnaire)).andExpect(status().isOk());
        mvc.perform(put("/api/parametres").header("Authorization", gestionnaire).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\"}")).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/parametres/logo").file(new MockMultipartFile("file", "l.png", "image/png", PNG))
                .header("Authorization", gestionnaire)).andExpect(status().isForbidden());
    }

    @Test
    void logo_envoiPuisLectureBinaire() throws Exception {
        String admin = adminBearer();
        drainAuditMessages();
        mvc.perform(get("/api/parametres/logo").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Aucun logo n'a été configuré"));

        mvc.perform(multipart("/api/parametres/logo").file(new MockMultipartFile("file", "logo.png", "image/png", PNG))
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasLogo").value(true));

        mvc.perform(get("/api/parametres/logo").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));
        assertThat(LOGO_DIR.resolve("logo.png")).exists();

        List<JsonNode> audit = drainAuditMessages();
        assertThat(audit).extracting(n -> n.get("action").asText()).containsExactly("UPLOAD_LOGO");
        assertThat(audit.getFirst().get("entityType").asText()).isEqualTo("Parametres");
    }

    @Test
    void logo_formatRefuseEtTailleSuperieureA2Mo() throws Exception {
        String admin = adminBearer();
        mvc.perform(multipart("/api/parametres/logo").file(new MockMultipartFile("file", "l.gif", "image/gif", PNG))
                        .header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Seuls les formats JPEG et PNG sont acceptés"));
        mvc.perform(multipart("/api/parametres/logo")
                        .file(new MockMultipartFile("file", "l.png", "image/png", new byte[2_500_000]))
                        .header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le logo dépasse la taille maximale de 2 Mo"));
    }

    /** Limite multipart appliquee par Tomcat : verifiee sur un vrai serveur HTTP, pas avec MockMvc. */
    @Test
    void logo_requeteDePlusDe3Mo_413FileTooLargeRecuParLeClient() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", adminBearer());
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.IMAGE_PNG);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(new ByteArrayResource(new byte[3_500_000]) {
            @Override
            public String getFilename() {
                return "enorme.png";
            }
        }, partHeaders));

        ResponseEntity<JsonNode> response = rest.postForEntity("/api/parametres/logo", new HttpEntity<>(body, headers), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().get("error").asText()).isEqualTo("FILE_TOO_LARGE");
        assertThat(response.getBody().get("message").asText()).isEqualTo("Le fichier dépasse la taille maximale autorisée.");
    }

    private String gestionnaireBearer() throws Exception {
        JsonNode created = json(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/users")
                .header("Authorization", adminBearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Gestion\",\"lastName\":\"Naire\"}")).andReturn());
        return bearer(login("gestion.naire", created.get("temporaryPassword").asText()));
    }
}
