package com.daara.internatadmin.finance.paiementsalaire;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PaiementSalaireRepository extends JpaRepository<PaiementSalaire, Long> {

    List<PaiementSalaire> findByEmployeIdOrderByPaymentDateDesc(Long employeId);

    List<PaiementSalaire> findByConcernedMonthAndStatus(LocalDate concernedMonth, StatutPaiementSalaire status);
    List<PaiementSalaire> findByStatus(StatutPaiementSalaire status);
}