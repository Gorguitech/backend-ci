package com.daara.internatadmin.auth.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(@NotBlank String firstName, @NotBlank String lastName) {
}
