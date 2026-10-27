package com.daara.internatadmin.common.security;

import com.daara.internatadmin.common.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Jeton present mais expire, mal forme ou mal signe : 401 INVALID_TOKEN.
 * C'est le seul cas ou un 401 est emis (le frontend deconnecte l'utilisateur sur tout 401).
 */
public class InvalidTokenEntryPoint implements AuthenticationEntryPoint {

    public static final ErrorResponse BODY = new ErrorResponse("INVALID_TOKEN", "Jeton invalide ou expiré.");

    private final ObjectMapper objectMapper;

    public InvalidTokenEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), BODY);
    }
}
