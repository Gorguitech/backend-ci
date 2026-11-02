package com.daara.internatadmin.parametres.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateParametresRequest(
        @NotBlank String name,
        String address,
        String phone,
        String email,
        String directorName
) {}