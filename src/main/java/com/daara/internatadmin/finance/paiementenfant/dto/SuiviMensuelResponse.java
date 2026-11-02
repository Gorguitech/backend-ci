package com.daara.internatadmin.finance.paiementenfant.dto;

import java.math.BigDecimal;
import java.util.List;

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
) {}