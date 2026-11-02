package com.daara.internatadmin.auth.dto;

public record RecoveryResetResponse(
        String message,
        String newRecoveryCode
) {}