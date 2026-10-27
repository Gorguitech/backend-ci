package com.daara.internatadmin.comptabilite.depense;

import com.daara.internatadmin.comptabilite.StatutMouvement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface DepenseRepository extends JpaRepository<Depense, Long> {

    List<Depense> findByStatusAndExpenseDateBetween(StatutMouvement status, LocalDate start, LocalDate end);

    List<Depense> findByStatusAndCategorieIdAndExpenseDateBetween(
            StatutMouvement status, Long categorieId, LocalDate start, LocalDate end);

    boolean existsByCategorieIdAndAmountAndExpenseDateAndRegisteredByUserIdAndCreatedAtAfter(
            Long categorieId, BigDecimal amount, LocalDate expenseDate, Long registeredByUserId, LocalDateTime after);

    /** Total des depenses valides d'un mois par nom de categorie (tresorerie) : lignes [nom, total]. */
    @Query("SELECT d.categorie.name, SUM(d.amount) FROM Depense d "
            + "WHERE d.status = :status AND d.expenseDate BETWEEN :start AND :end GROUP BY d.categorie.name")
    List<Object[]> sumByCategorieName(@Param("status") StatutMouvement status,
                                      @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Depense d WHERE d.status = :status")
    BigDecimal sumByStatus(@Param("status") StatutMouvement status);
}
