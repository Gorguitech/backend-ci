package com.daara.internatadmin.auth.user.dto;

/** recoveryCode : null sauf lors de la toute premiere generation pour un ADMIN. */
public record ChangePasswordResponse(String message, String recoveryCode) {
}
