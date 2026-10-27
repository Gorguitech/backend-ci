package com.daara.internatadmin.common.util;

/** Format des matricules : ENF-2026-0001, EMP-2026-0001. */
public final class MatriculeGenerator {

    private MatriculeGenerator() {
    }

    public static String format(String prefix, int year, long sequence) {
        return String.format("%s-%d-%04d", prefix, year, sequence);
    }

    /** Prefixe commun a tous les matricules d'une annee, utilise pour compter la sequence. */
    public static String yearPrefix(String prefix, int year) {
        return prefix + "-" + year;
    }
}
