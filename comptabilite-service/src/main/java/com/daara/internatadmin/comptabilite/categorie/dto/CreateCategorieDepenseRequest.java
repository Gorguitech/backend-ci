package com.daara.internatadmin.comptabilite.categorie.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCategorieDepenseRequest(@NotBlank String name, String description) {
}
