package com.daara.internatadmin.comptabilite.categorie.dto;

/** Categorie de depense (CategorieDepense du frontend) ; description peut etre null. */
public record CategorieDepenseResponse(Long id, String name, String description, boolean active) {
}
