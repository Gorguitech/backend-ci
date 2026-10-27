package com.daara.internatadmin.common.security;

/**
 * Noms des claims, identiques a ceux du monolithe : le frontend decode lui-meme le jeton
 * et lit sub, userId, role, mustChangePassword et exp. Aucun claim ne doit etre ajoute ou renomme.
 */
public final class JwtClaims {

    public static final String USER_ID = "userId";
    public static final String ROLE = "role";
    public static final String MUST_CHANGE_PASSWORD = "mustChangePassword";

    private JwtClaims() {
    }
}
