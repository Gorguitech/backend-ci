package com.daara.internatadmin.auth.dto;

public record LoginResponse(
        String token,
        String username,
        String fullName,
        String role,
        boolean mustChangePassword
) {}