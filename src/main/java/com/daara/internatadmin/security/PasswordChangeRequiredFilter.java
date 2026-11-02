package com.daara.internatadmin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final String ALLOWED_PATH = "/api/users/me/password";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            boolean mustChange = userDetails.isMustChangePassword();
            boolean isChangePasswordRoute = request.getServletPath().equals(ALLOWED_PATH);

            if (mustChange && !isChangePasswordRoute) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"MUST_CHANGE_PASSWORD\",\"message\":\"Changement de mot de passe obligatoire.\"}"
                );
                return;
            }
        }
        chain.doFilter(request, response);
    }
}