package com.daara.internatadmin.common.security;

/**
 * Utilisateur authentifie, reconstruit a partir des claims du JWT (userId, sub, role).
 * Injecte dans les controleurs par {@code @AuthenticationPrincipal CurrentUser}.
 */
public record CurrentUser(Long id, String username, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
