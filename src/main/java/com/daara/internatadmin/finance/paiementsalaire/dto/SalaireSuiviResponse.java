package com.daara.internatadmin.finance.paiementsalaire.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalaireSuiviResponse(
        Long employeId,
        String matricule,
        String fullName,
        BigDecimal montantAttendu,
        String statut,          // "PAYE" ou "IMPAYE"
        LocalDate paymentDate,
        Long paiementId
) {}