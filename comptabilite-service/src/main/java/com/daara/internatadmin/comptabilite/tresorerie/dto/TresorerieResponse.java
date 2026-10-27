package com.daara.internatadmin.comptabilite.tresorerie.dto;

import java.math.BigDecimal;
import java.util.List;

/** Tresorerie d'un mois (Tresorerie et LigneDetail du frontend). */
public record TresorerieResponse(
        int year,
        int month,
        BigDecimal totalRecettes,
        BigDecimal totalDepenses,
        BigDecimal solde,
        List<LigneDetail> detailRecettes,
        List<LigneDetail> detailDepenses
) {
    public record LigneDetail(String source, BigDecimal montant) {
    }
}
