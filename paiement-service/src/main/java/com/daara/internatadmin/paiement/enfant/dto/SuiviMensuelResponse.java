package com.daara.internatadmin.paiement.enfant.dto;

import java.math.BigDecimal;
import java.util.List;

/** Ligne du suivi des mensualites (SuiviMensuel du frontend) ; statutPaiement : PAYE, PARTIEL ou IMPAYE. */
public record SuiviMensuelResponse(
        Long enfantId,
        String matricule,
        String fullName,
        String guardianName,
        String guardianPhone,
        BigDecimal montantDu,
        BigDecimal montantPaye,
        BigDecimal montantRestant,
        BigDecimal excedent,
        String statutPaiement,
        List<Long> paiementIds
) {
}
