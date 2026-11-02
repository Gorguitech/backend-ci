package com.daara.internatadmin.finance.categoriedepense.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCategorieDepenseRequest(@NotBlank String name, String description) {}