package com.daara.internatadmin.paiement.salaire;

import com.daara.internatadmin.paiement.StatutPaiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PaiementSalaireRepository extends JpaRepository<PaiementSalaire, Long> {

    List<PaiementSalaire> findByEmployeIdOrderByPaymentDateDesc(Long employeId);

    List<PaiementSalaire> findByConcernedMonthAndStatus(LocalDate concernedMonth, StatutPaiement status);

    /** Salaires valides d'un mois concerne (tresorerie mensuelle, comme le monolithe). */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaiementSalaire p WHERE p.status = :status AND p.concernedMonth = :month")
    BigDecimal sumByStatusAndConcernedMonth(@Param("status") StatutPaiement status, @Param("month") LocalDate month);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaiementSalaire p WHERE p.status = :status")
    BigDecimal sumByStatus(@Param("status") StatutPaiement status);
}
