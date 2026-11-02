package com.daara.internatadmin.finance.paiementsalaire.dto;

import com.daara.internatadmin.finance.paiementenfant.ModePaiement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecuSalaireResponse(
        Long paiementId,
        String employeMatricule,
        String employeFullName,
        String employeFunction,
        ModePaiement paymentMethod,
        BigDecimal amount,
        LocalDate concernedMonth,
        LocalDate paymentDate,
        String registeredByUsername
) {}