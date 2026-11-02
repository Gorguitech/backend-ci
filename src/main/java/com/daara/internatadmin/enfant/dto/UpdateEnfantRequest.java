package com.daara.internatadmin.enfant.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateEnfantRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        LocalDate birthDate,
        @Min(0) @Max(30) Integer approximateAge,
        @NotBlank @Pattern(regexp = "M|F") String gender,
        @NotBlank String guardianName,
        @NotBlank String guardianPhone,
        String neighborhood,
        String roomGroup,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal registrationFee,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal monthlyFee
) {}