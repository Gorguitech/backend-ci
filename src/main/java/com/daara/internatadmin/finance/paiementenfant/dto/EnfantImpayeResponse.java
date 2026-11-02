package com.daara.internatadmin.finance.paiementenfant.dto;

public record EnfantImpayeResponse(
        Long enfantId,
        String matricule,
        String fullName,
        String guardianName,
        String guardianPhone
) {}