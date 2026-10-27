package com.daara.internatadmin.common.feign;

import com.daara.internatadmin.common.security.InternalApiKeyFilter;
import com.daara.internatadmin.common.web.RequestIdFilter;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Appels inter-services : relaie le JWT de l'utilisateur (tracabilite, roles), ajoute la cle interne
 * exigee sur /internal/** et propage l'identifiant de correlation.
 */
public class FeignRelayInterceptor implements RequestInterceptor {

    private final String internalApiKey;

    public FeignRelayInterceptor(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header(InternalApiKeyFilter.HEADER, internalApiKey);

        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            template.header(RequestIdFilter.HEADER, requestId);
        }

        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
    }
}
