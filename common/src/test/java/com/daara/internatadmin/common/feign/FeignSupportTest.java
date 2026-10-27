package com.daara.internatadmin.common.feign;

import com.daara.internatadmin.common.error.ServiceUnavailableException;
import com.daara.internatadmin.common.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeignSupportTest {

    private final InternalErrorDecoder decoder = new InternalErrorDecoder(new ObjectMapper());

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void intercepteur_relaieLeJetonLaCleInterneEtLIdentifiantDeCorrelation() {
        MockHttpServletRequest incoming = new MockHttpServletRequest();
        incoming.addHeader("Authorization", "Bearer abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));
        MDC.put(RequestIdFilter.MDC_KEY, "req-1");

        RequestTemplate template = new RequestTemplate();
        new FeignRelayInterceptor("cle-interne").apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer abc");
        assertThat(template.headers().get("X-Internal-Key")).containsExactly("cle-interne");
        assertThat(template.headers().get("X-Request-Id")).containsExactly("req-1");
    }

    @Test
    void intercepteur_horsRequeteHttp_cleInterneSeulement() {
        RequestTemplate template = new RequestTemplate();
        new FeignRelayInterceptor("cle-interne").apply(template);

        assertThat(template.headers()).containsOnlyKeys("X-Internal-Key");
    }

    @Test
    void decodeur_400InvalidRequest_devientIllegalArgumentAvecLeMemeMessage() {
        Exception ex = decoder.decode("EnfantClient#get", response(400,
                "{\"error\":\"INVALID_REQUEST\",\"message\":\"Enfant introuvable : 12\"}"));

        assertThat(ex).isInstanceOf(IllegalArgumentException.class).hasMessage("Enfant introuvable : 12");
    }

    @Test
    void decodeur_autre400_devient503() {
        assertThat(decoder.decode("m", response(400, "{\"error\":\"AUTRE\",\"message\":\"x\"}")))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(decoder.decode("m", response(400, "pas du json")))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThat(decoder.decode("m", response(400, null)))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void decodeur_5xxEt403_deviennent503() {
        assertThat(decoder.decode("m", response(500, "{}"))).isInstanceOf(ServiceUnavailableException.class);
        assertThat(decoder.decode("m", response(503, "{}"))).isInstanceOf(ServiceUnavailableException.class);
        assertThat(decoder.decode("m", response(403, "{}"))).isInstanceOf(ServiceUnavailableException.class);
    }

    private static Response response(int status, String body) {
        Request request = Request.create(Request.HttpMethod.GET, "http://enfant-service/internal/enfants/12",
                Map.of(), null, StandardCharsets.UTF_8, null);
        Response.Builder builder = Response.builder().status(status).request(request).headers(Map.of());
        if (body != null) {
            builder.body(body, StandardCharsets.UTF_8);
        }
        return builder.build();
    }
}
