package com.daara.internatadmin.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, message = "Le nouveau mot de passe doit contenir au moins 8 caractères")
        String newPassword
) {}