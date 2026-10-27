package com.daara.internatadmin.comptabilite.depense.dto;

import com.daara.internatadmin.comptabilite.StatutMouvement;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Depense (Depense du frontend), aussi renvoyee comme donnees du recu ; description peut etre null. */
public record DepenseResponse(
        Long id,
        Long categorieId,
        String categorieName,
        BigDecimal amount,
        LocalDate expenseDate,
        String description,
        String registeredByUsername,
        StatutMouvement status
) {
}
