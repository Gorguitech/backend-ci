package com.daara.internatadmin.common;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** E1 : une collision ne doit pas condamner les tentatives suivantes. */
class TransactionalRetryTest {

    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionalRetry retry = new TransactionalRetry(transactionManager);

    @Test
    void collision_nouvelEssaiDansUneNouvelleTransaction() {
        when(transactionManager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        List<Integer> attempts = new ArrayList<>();

        String result = retry.onUniqueViolation(5, "matricule", attempt -> {
            attempts.add(attempt);
            if (attempt == 0) {
                throw new DataIntegrityViolationException("matricule deja pris");
            }
            return "ENF-2026-0002";
        });

        assertThat(result).isEqualTo("ENF-2026-0002");
        assertThat(attempts).containsExactly(0, 1);
        verify(transactionManager, times(2)).getTransaction(any());
        verify(transactionManager, times(1)).rollback(any());
        verify(transactionManager, times(1)).commit(any());
    }

    @Test
    void tentativesEpuisees_erreurTechniqueEtJamaisUnDoublon() {
        when(transactionManager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());

        assertThatThrownBy(() -> retry.onUniqueViolation(3, "matricule", attempt -> {
            throw new DataIntegrityViolationException("matricule deja pris");
        }))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(DuplicateEntityException.class)
                .hasMessageContaining("3 tentatives");
        verify(transactionManager, times(3)).rollback(any());
    }
}
