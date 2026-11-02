package com.daara.internatadmin.common;

import java.text.Normalizer;

public class UsernameGenerator {

    public static String generateBase(String firstName, String lastName) {
        return normalize(firstName) + "." + normalize(lastName);
    }

    private static String normalize(String input) {
        String noAccents = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase()
                .replaceAll("[^a-z0-9]", "");
    }
}