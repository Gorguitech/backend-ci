package com.daara.internatadmin.finance.paiementsalaire.dto;

import com.daara.internatadmin.finance.paiementenfant.ModePaiement;
import com.daara.internatadmin.finance.paiementsalaire.StatutPaiementSalaire;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaiementSalaireResponse(
        Long id,
        Long employeId,
        String employeFullName,
        String employeMatricule,
        ModePaiement paymentMethod,
        BigDecimal amount,
        LocalDate concernedMonth,
        LocalDate paymentDate,
        String registeredByUsername,
        StatutPaiementSalaire status
) {}