package com.daara.internatadmin.user.dto;

public record ChangePasswordResponse(
        String message,
        String recoveryCode // null sauf lors de la toute première génération pour un Admin
) {}