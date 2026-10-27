package com.daara.internatadmin.enfant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateEnfantRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        LocalDate birthDate,
        @Min(0) @Max(30) Integer approximateAge,
        @NotBlank @Pattern(regexp = "M|F", message = "Le genre doit être 'M' ou 'F'") String gender,
        @NotBlank String guardianName,
        @NotBlank String guardianPhone,
        String neighborhood,
        @NotNull LocalDate entryDate,
        String roomGroup,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal registrationFee,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal monthlyFee
) {
}
