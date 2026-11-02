package com.daara.internatadmin.user.dto;

public record CreateUserResponse(
        String username,
        String temporaryPassword,
        String fullName
) {}