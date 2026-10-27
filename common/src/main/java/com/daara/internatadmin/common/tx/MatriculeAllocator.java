package com.daara.internatadmin.common.tx;

import com.daara.internatadmin.common.util.MatriculeGenerator;

import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Attribution d'un matricule PREFIXE-annee-sequence (ENF-2026-0001, EMP-2026-0001), regle du monolithe :
 * la sequence vaut le nombre de matricules de l'annee plus un. En cas de collision sur la contrainte UNIQUE,
 * la sequence suivante est essayee dans une nouvelle transaction ; apres 5 tentatives, echec technique (500).
 */
public class MatriculeAllocator {

    static final int MAX_ATTEMPTS = 5;

    private final TransactionalRetry transactionalRetry;

    public MatriculeAllocator(TransactionalRetry transactionalRetry) {
        this.transactionalRetry = transactionalRetry;
    }

    /**
     * @param prefix        ENF ou EMP
     * @param year          annee d'entree ou d'embauche
     * @param countByPrefix nombre de matricules commencant par PREFIXE-annee
     * @param insert        enregistre l'entite avec le matricule recu (saveAndFlush) et renvoie la reponse
     */
    public <T> T allocate(String prefix, int year, ToLongFunction<String> countByPrefix, Function<String, T> insert) {
        String yearPrefix = MatriculeGenerator.yearPrefix(prefix, year);
        return transactionalRetry.onUniqueViolation(MAX_ATTEMPTS, "matricule", attempt -> {
            long sequence = countByPrefix.applyAsLong(yearPrefix) + 1 + attempt;
            return insert.apply(MatriculeGenerator.format(prefix, year, sequence));
        });
    }
}
