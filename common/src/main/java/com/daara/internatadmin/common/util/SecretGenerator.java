package com.daara.internatadmin.common.util;

import java.security.SecureRandom;

/** Mots de passe temporaires et codes de recuperation, destines a etre recopies a la main. */
public final class SecretGenerator {

    // caracteres ambigus (0/O, 1/l/I) exclus volontairement
    static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretGenerator() {
    }

    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
