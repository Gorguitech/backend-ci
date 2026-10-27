package com.daara.internatadmin.auth.user;

import java.text.Normalizer;

/** prenom.nom sans accents ni caracteres speciaux, comme dans le monolithe. */
final class UsernameGenerator {

    private UsernameGenerator() {
    }

    static String generateBase(String firstName, String lastName) {
        return normalize(firstName) + "." + normalize(lastName);
    }

    private static String normalize(String input) {
        String noAccents = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
