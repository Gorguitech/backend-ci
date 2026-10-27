package com.daara.internatadmin.common.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractWebTest {

    public static final String INTERNAL_KEY = "cle-interne-de-test";

    @Autowired
    protected MockMvc mvc;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("internatadmin.security.jwt-public-key-b64", TestJwts::publicKeyB64);
        registry.add("internatadmin.security.internal-api-key", () -> INTERNAL_KEY);
        registry.add("internatadmin.security.public-paths", () -> "/api/public/**");
    }
}
