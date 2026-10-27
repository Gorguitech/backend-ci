package com.daara.internatadmin.paiement.enfant;

import com.daara.internatadmin.paiement.StatutPaiement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PaiementEnfantRepository extends JpaRepository<PaiementEnfant, Long> {

    List<PaiementEnfant> findByEnfantIdAndPaymentTypeOrderByPaymentDateDesc(Long enfantId, TypePaiementEnfant paymentType);

    List<PaiementEnfant> findByEnfantIdAndPaymentTypeAndConcernedMonthAndStatus(
            Long enfantId, TypePaiementEnfant paymentType, LocalDate concernedMonth, StatutPaiement status);

    List<PaiementEnfant> findByPaymentTypeAndConcernedMonthAndStatus(
            TypePaiementEnfant paymentType, LocalDate concernedMonth, StatutPaiement status);

    List<PaiementEnfant> findByPaymentTypeAndStatus(TypePaiementEnfant paymentType, StatutPaiement status);

    /** Encaissements valides par date de paiement (tresorerie mensuelle, comme le monolithe). */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaiementEnfant p "
            + "WHERE p.status = :status AND p.paymentDate BETWEEN :start AND :end")
    BigDecimal sumByStatusAndPaymentDateBetween(@Param("status") StatutPaiement status,
                                                @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaiementEnfant p WHERE p.status = :status")
    BigDecimal sumByStatus(@Param("status") StatutPaiement status);
}
