package com.daara.internatadmin.common.error;

/**
 * Un service dont depend la requete ne repond pas ou repond en erreur.
 * Toujours traduit en 503, jamais en 401 : le frontend deconnecte l'utilisateur sur tout 401.
 */
public class ServiceUnavailableException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "Service temporairement indisponible. Veuillez réessayer.";

    public ServiceUnavailableException() {
        super(DEFAULT_MESSAGE);
    }

    public ServiceUnavailableException(Throwable cause) {
        super(DEFAULT_MESSAGE, cause);
    }
}
