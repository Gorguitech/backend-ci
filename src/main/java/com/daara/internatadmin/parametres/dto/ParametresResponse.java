package com.daara.internatadmin.parametres.dto;

public record ParametresResponse(
        Long id,
        String name,
        String address,
        String phone,
        String email,
        String directorName,
        boolean hasLogo
) {}