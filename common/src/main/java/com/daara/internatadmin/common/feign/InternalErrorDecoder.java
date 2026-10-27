package com.daara.internatadmin.common.feign;

import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.common.error.ServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Traduction des erreurs d'un service appele :
 * <ul>
 *   <li>400 INVALID_REQUEST (par exemple « Enfant introuvable : 12 ») : relance en IllegalArgumentException avec
 *       le meme message, pour que l'appelant reponde exactement comme le monolithe ;</li>
 *   <li>tout autre statut : ServiceUnavailableException (503).</li>
 * </ul>
 */
public class InternalErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(InternalErrorDecoder.class);

    private final ObjectMapper objectMapper;

    public InternalErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 400) {
            ErrorResponse body = readBody(response);
            if (body != null && "INVALID_REQUEST".equals(body.error())) {
                return new IllegalArgumentException(body.message());
            }
        }
        log.warn("Appel {} en echec : statut {}", methodKey, response.status());
        return new ServiceUnavailableException();
    }

    private ErrorResponse readBody(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream in = response.body().asInputStream()) {
            return objectMapper.readValue(in, ErrorResponse.class);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
