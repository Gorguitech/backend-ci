package com.daara.internatadmin.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/** Ignore l'en-tete Authorization sur les chemins publics : un ancien jeton ne doit pas bloquer le login. */
public class PublicPathsBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final List<String> publicPaths;

    public PublicPathsBearerTokenResolver(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String pattern : publicPaths) {
            if (matcher.match(pattern, path)) {
                return null;
            }
        }
        return delegate.resolve(request);
    }
}
