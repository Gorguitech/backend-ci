package com.daara.internatadmin.finance.paiementenfant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PaiementEnfantRepository extends JpaRepository<PaiementEnfant, Long> {

    List<PaiementEnfant> findByEnfantIdOrderByPaymentDateDesc(Long enfantId);

    @Query("""
    SELECT p.enfant.id, SUM(p.amount), MAX(p.expectedAmount) FROM PaiementEnfant p
    WHERE p.paymentType = com.daara.internatadmin.finance.paiementenfant.TypePaiementEnfant.MENSUALITE
    AND p.concernedMonth = :month
    AND p.status = com.daara.internatadmin.finance.paiementenfant.StatutPaiement.VALIDE
    GROUP BY p.enfant.id
    """)
    List<Object[]> sumMensualitesByEnfantForMonth(@Param("month") LocalDate month);

    List<PaiementEnfant> findByEnfantIdAndPaymentTypeOrderByPaymentDateDesc(Long enfantId, TypePaiementEnfant paymentType);
    // dans PaiementEnfantRepository.java
    List<PaiementEnfant> findByPaymentTypeAndStatus(TypePaiementEnfant paymentType, StatutPaiement status);
    List<PaiementEnfant> findByPaymentTypeAndConcernedMonthAndStatus(
            TypePaiementEnfant paymentType, LocalDate concernedMonth, StatutPaiement status
    );
    List<PaiementEnfant> findByPaymentDateBetweenAndStatus(LocalDate start, LocalDate end, StatutPaiement status);
    List<PaiementEnfant> findByStatus(StatutPaiement status);
}