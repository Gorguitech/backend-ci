package com.daara.internatadmin.common.tx;

import com.daara.internatadmin.common.support.TestTransactionManager;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatriculeAllocatorTest {

    private final TestTransactionManager transactionManager = new TestTransactionManager();
    private final MatriculeAllocator allocator = new MatriculeAllocator(new TransactionalRetry(transactionManager));

    @Test
    void sequenceEgaleAuNombreDeMatriculesDeLAnneePlusUn() {
        List<String> prefixes = new ArrayList<>();

        String matricule = allocator.allocate("EMP", 2025, prefix -> {
            prefixes.add(prefix);
            return 41;
        }, m -> m);

        assertThat(matricule).isEqualTo("EMP-2025-0042");
        assertThat(prefixes).containsExactly("EMP-2025");
    }

    @Test
    void collision_sequenceSuivanteDansUneNouvelleTransaction() {
        List<String> tried = new ArrayList<>();

        String matricule = allocator.allocate("ENF", 2026, prefix -> 2, m -> {
            tried.add(m);
            if (tried.size() < 3) {
                throw new DataIntegrityViolationException("duplicate key");
            }
            return m;
        });

        assertThat(tried).containsExactly("ENF-2026-0003", "ENF-2026-0004", "ENF-2026-0005");
        assertThat(matricule).isEqualTo("ENF-2026-0005");
        assertThat(transactionManager.begins.get()).isEqualTo(3);
    }

    @Test
    void cinqCollisions_echecTechnique() {
        assertThatThrownBy(() -> allocator.allocate("EMP", 2026, prefix -> 0, m -> {
            throw new DataIntegrityViolationException("duplicate key");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Impossible de générer un matricule unique après 5 tentatives");
        assertThat(transactionManager.begins.get()).isEqualTo(5);
    }
}
