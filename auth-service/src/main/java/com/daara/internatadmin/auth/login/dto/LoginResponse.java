package com.daara.internatadmin.auth.login.dto;

public record LoginResponse(String token, String username, String fullName, String role, boolean mustChangePassword) {
}
