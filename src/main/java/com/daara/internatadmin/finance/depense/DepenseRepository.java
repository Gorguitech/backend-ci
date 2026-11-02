package com.daara.internatadmin.finance.depense;

import com.daara.internatadmin.finance.StatutMouvement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface DepenseRepository extends JpaRepository<Depense, Long> {

    List<Depense> findByStatusAndExpenseDateBetween(StatutMouvement status, LocalDate start, LocalDate end);

    List<Depense> findByStatusAndCategorieIdAndExpenseDateBetween(
            StatutMouvement status, Long categorieId, LocalDate start, LocalDate end
    );


    boolean existsByCategorieIdAndAmountAndExpenseDateAndRegisteredByUserIdAndCreatedAtAfter(
            Long categorieId, BigDecimal amount, LocalDate expenseDate, Long registeredByUserId, LocalDateTime after
    );

    List<Depense> findByStatus(StatutMouvement status);
}