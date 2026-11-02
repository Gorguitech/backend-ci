package com.daara.internatadmin.finance.tresorerie.dto;

import java.math.BigDecimal;

public record SoldeGlobalResponse(
        BigDecimal totalRecettes,
        BigDecimal totalDepenses,
        BigDecimal solde
) {}