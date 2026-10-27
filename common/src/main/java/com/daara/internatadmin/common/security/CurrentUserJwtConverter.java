package com.daara.internatadmin.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.List;

/** Construit le {@link CurrentUser} et l'autorite ROLE_ADMIN ou ROLE_GESTIONNAIRE a partir des claims. */
public class CurrentUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Object userId = jwt.getClaims().get(JwtClaims.USER_ID);
        String role = jwt.getClaimAsString(JwtClaims.ROLE);
        String username = jwt.getSubject();

        if (!(userId instanceof Number number) || role == null || username == null) {
            throw new InvalidBearerTokenException("Claims obligatoires absents");
        }

        CurrentUser user = new CurrentUser(number.longValue(), username, role);
        return UsernamePasswordAuthenticationToken.authenticated(
                user, jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
