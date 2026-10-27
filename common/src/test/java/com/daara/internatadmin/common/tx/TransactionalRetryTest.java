package com.daara.internatadmin.common.tx;

import com.daara.internatadmin.common.support.TestTransactionManager;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionalRetryTest {

    private final TestTransactionManager transactionManager = new TestTransactionManager();
    private final TransactionalRetry retry = new TransactionalRetry(transactionManager);

    @Test
    void premiereTentativeReussie_uneSeuleTransaction() {
        String result = retry.onUniqueViolation(5, "matricule", attempt -> "ENF-2026-000" + (attempt + 1));

        assertThat(result).isEqualTo("ENF-2026-0001");
        assertThat(transactionManager.begins.get()).isEqualTo(1);
        assertThat(transactionManager.commits.get()).isEqualTo(1);
    }

    @Test
    void collision_nouvelleTentativeDansUneNouvelleTransaction() {
        List<Integer> attempts = new ArrayList<>();

        String result = retry.onUniqueViolation(5, "matricule", attempt -> {
            attempts.add(attempt);
            if (attempt == 0) {
                throw new DataIntegrityViolationException("duplicate key matricule");
            }
            return "ENF-2026-0002";
        });

        assertThat(result).isEqualTo("ENF-2026-0002");
        assertThat(attempts).containsExactly(0, 1);
        assertThat(transactionManager.begins.get()).as("une transaction par tentative").isEqualTo(2);
        assertThat(transactionManager.rollbacks.get()).isEqualTo(1);
        assertThat(transactionManager.commits.get()).isEqualTo(1);
    }

    @Test
    void cinqCollisions_illegalStateExceptionEtJamaisUneErreurMetier() {
        assertThatThrownBy(() -> retry.onUniqueViolation(5, "matricule", attempt -> {
            throw new DataIntegrityViolationException("duplicate key");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Impossible de générer un matricule unique après 5 tentatives");

        assertThat(transactionManager.begins.get()).isEqualTo(5);
        assertThat(transactionManager.rollbacks.get()).isEqualTo(5);
    }

    @Test
    void autreException_propageeSansNouvelEssai() {
        assertThatThrownBy(() -> retry.onUniqueViolation(5, "matricule", attempt -> {
            throw new IllegalArgumentException("refus");
        })).isInstanceOf(IllegalArgumentException.class);

        assertThat(transactionManager.begins.get()).isEqualTo(1);
    }
}
