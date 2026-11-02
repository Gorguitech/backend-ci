package com.daara.internatadmin.user.dto;

public record UserSummaryResponse(
        Long id,
        String username,
        String fullName,
        String role,
        boolean active,
        boolean mustChangePassword
) {}