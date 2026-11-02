package com.daara.internatadmin.common;

import java.security.SecureRandom;

public class SecretGenerator {
    // caractères ambigus (0/O, 1/l/I) exclus volontairement : ce mot de passe sera recopié à la main
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}