package com.daara.internatadmin.auth;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ServiceContractIT extends AbstractIntegrationTest {

    @Test
    void health_publicEtUp() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApi_cheminsDuMonolitheEtCodesDocumentes() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/reset-with-recovery-code'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/users'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/users'].get").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}/reset-password'].post").exists())
                .andExpect(jsonPath("$.paths['/api/users/me/password'].put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}/deactivate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}/activate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/users/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/users/stats'].get").exists())
                .andExpect(jsonPath("$.paths['/api/parametres'].get").exists())
                .andExpect(jsonPath("$.paths['/api/parametres'].put").exists())
                .andExpect(jsonPath("$.paths['/api/parametres/logo'].post.responses['413']").exists())
                .andExpect(jsonPath("$.paths['/api/parametres/logo'].get").exists())
                .andExpect(jsonPath("$.paths['/api/users'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/internal/users/{id}/state']").doesNotExist());
    }
}
