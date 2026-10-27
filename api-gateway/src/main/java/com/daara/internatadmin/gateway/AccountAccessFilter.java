package com.daara.internatadmin.gateway;

import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.common.error.ServiceUnavailableException;
import com.daara.internatadmin.common.security.InvalidTokenEntryPoint;
import com.daara.internatadmin.common.security.JwtClaims;
import com.daara.internatadmin.common.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Controle d'acces de la gateway, dans l'ordre :
 * <ol>
 *   <li>chemins publics (login, recuperation, sante, documentation) et requetes CORS preliminaires : laisses passer ;</li>
 *   <li>sans jeton : 403, comme le monolithe ;</li>
 *   <li>jeton expire, mal forme ou mal signe : 401 INVALID_TOKEN (le frontend deconnecte alors l'utilisateur) ;</li>
 *   <li>etat du compte lu dans auth-service : desactive ou inconnu, 403 ; changement de mot de passe obligatoire,
 *       403 MUST_CHANGE_PASSWORD sauf sur la route de changement ;</li>
 *   <li>auth-service indisponible : 503, jamais 401.</li>
 * </ol>
 */
public class AccountAccessFilter extends OncePerRequestFilter {

    static final String CHANGE_PASSWORD_PATH = "/api/users/me/password";
    static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/**", "/health", "/actuator/health/**", "/error",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**");

    private static final ErrorResponse MUST_CHANGE_PASSWORD =
            new ErrorResponse("MUST_CHANGE_PASSWORD", "Changement de mot de passe obligatoire.");
    private static final ErrorResponse SERVICE_UNAVAILABLE =
            new ErrorResponse("SERVICE_UNAVAILABLE", ServiceUnavailableException.DEFAULT_MESSAGE);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final AccountStateClient accountStateClient;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public AccountAccessFilter(JwtDecoder jwtDecoder, AccountStateClient accountStateClient, ObjectMapper objectMapper) {
        this.jwtDecoder = jwtDecoder;
        this.accountStateClient = accountStateClient;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = path(request);
        return PUBLIC_PATHS.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        Long userId = decodeUserId(authorization.substring(BEARER_PREFIX.length()));
        if (userId == null) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, InvalidTokenEntryPoint.BODY);
            return;
        }

        Optional<AccountStateClient.AccountState> state;
        try {
            state = accountStateClient.fetch(userId, authorization, MDC.get(RequestIdFilter.MDC_KEY));
        } catch (ServiceUnavailableException e) {
            write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE);
            return;
        }

        if (state.isEmpty() || !state.get().active()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (state.get().mustChangePassword() && !CHANGE_PASSWORD_PATH.equals(path(request))) {
            write(response, HttpServletResponse.SC_FORBIDDEN, MUST_CHANGE_PASSWORD);
            return;
        }

        chain.doFilter(request, response);
    }

    private Long decodeUserId(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            Object userId = jwt.getClaims().get(JwtClaims.USER_ID);
            return (userId instanceof Number number) ? number.longValue() : null;
        } catch (JwtException e) {
            return null;
        }
    }

    private void write(HttpServletResponse response, int status, ErrorResponse body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
