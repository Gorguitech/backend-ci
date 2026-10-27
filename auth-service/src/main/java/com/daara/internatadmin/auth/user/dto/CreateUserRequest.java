package com.daara.internatadmin.auth.user.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(@NotBlank String firstName, @NotBlank String lastName) {
}
