package com.daara.internatadmin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecoveryResetRequest(
        @NotBlank String username,
        @NotBlank String recoveryCode,
        @NotBlank @Size(min = 8) String newPassword
) {}