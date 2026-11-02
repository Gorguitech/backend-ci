package com.daara.internatadmin.finance.paiementenfant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InscriptionSuiviResponse(
        Long enfantId,
        String matricule,
        String fullName,
        BigDecimal montantAttendu,
        String statut,          // "PAYE" ou "IMPAYE"
        LocalDate paymentDate,  // null si IMPAYE
        Long paiementId         // null si IMPAYE
) {}