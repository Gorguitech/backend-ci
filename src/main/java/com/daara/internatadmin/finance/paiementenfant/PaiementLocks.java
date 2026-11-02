package com.daara.internatadmin.finance.paiementenfant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;

/**
 * Verrou consultatif PostgreSQL par eleve et par mois, pris dans la transaction du paiement et libere a son commit.
 * Il serialise les paiements simultanes d'une meme mensualite : sans lui, deux paiements partiels lus au meme
 * moment pourraient ensemble depasser le montant du (l'index unique des mensualites a ete supprime en V10).
 * Si le verrou n'est pas obtenu dans le delai : CannotAcquireLockException, traduite en 503 (jamais 409).
 */
@Component
public class PaiementLocks {

    /** lock_not_available : delai lock_timeout depasse. */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    private final JdbcTemplate jdbcTemplate;
    private final long lockTimeoutMillis;

    public PaiementLocks(JdbcTemplate jdbcTemplate,
                         @Value("${app.paiement.lock-timeout:5s}") Duration lockTimeout) {
        this.jdbcTemplate = jdbcTemplate;
        this.lockTimeoutMillis = lockTimeout.toMillis();
    }

    /** A appeler dans une transaction ouverte. */
    public void lockMensualite(long enfantId, LocalDate concernedMonth) {
        int monthKey = concernedMonth.getYear() * 100 + concernedMonth.getMonthValue();
        try {
            jdbcTemplate.execute("SET LOCAL lock_timeout = " + lockTimeoutMillis);
            jdbcTemplate.query("SELECT pg_advisory_xact_lock(?, ?)", rs -> null, Math.toIntExact(enfantId), monthKey);
        } catch (DataAccessException e) {
            // Spring ne classe pas cet etat SQL : sans cette traduction, l'erreur deviendrait un 500
            if (e.getMostSpecificCause() instanceof SQLException sql && LOCK_NOT_AVAILABLE.equals(sql.getSQLState())) {
                throw new CannotAcquireLockException("Mensualite deja en cours d'enregistrement : enfant "
                        + enfantId + ", mois " + monthKey, e);
            }
            throw e;
        }
    }
}
