package com.daara.internatadmin.dashboard.dto;

import java.math.BigDecimal;

public record GlobalDashboardResponse(
        long nombreEnfantsActifs,
        long nombreEmployesActifs,
        BigDecimal totalRecettes,
        BigDecimal totalDepenses,
        BigDecimal soldeDisponible,
        long nombreImpayesMoisCourant
) {}