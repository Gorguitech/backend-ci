package com.daara.internatadmin.common.tx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.IntFunction;

/**
 * Rejoue une insertion soumise a une contrainte UNIQUE (matricule, username).
 * Chaque tentative s'execute dans sa propre transaction : apres une violation de contrainte,
 * la transaction en cours est inutilisable, un nouvel essai dans la meme transaction echouerait toujours.
 * Apres la derniere tentative, une IllegalStateException (500) est levee, jamais un 409.
 */
public class TransactionalRetry {

    private static final Logger log = LoggerFactory.getLogger(TransactionalRetry.class);

    private final TransactionTemplate transactionTemplate;

    public TransactionalRetry(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * @param maxAttempts nombre maximal de tentatives
     * @param attempt     action recevant le numero de tentative (a partir de 0)
     */
    public <T> T onUniqueViolation(int maxAttempts, String description, IntFunction<T> attempt) {
        for (int i = 0; i < maxAttempts; i++) {
            final int current = i;
            try {
                return transactionTemplate.execute(status -> attempt.apply(current));
            } catch (DataIntegrityViolationException e) {
                log.info("Collision sur {} (tentative {}/{}), nouvel essai", description, current + 1, maxAttempts);
            }
        }
        throw new IllegalStateException(
                "Impossible de générer un " + description + " unique après " + maxAttempts + " tentatives");
    }
}
