package com.daara.internatadmin.paiement.salaire.dto;

import com.daara.internatadmin.paiement.ModePaiement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Le montant n'est pas saisi : c'est le salaire de base de l'employe au moment du paiement. */
public record CreatePaiementSalaireRequest(
        @NotNull Long employeId,
        @NotNull ModePaiement paymentMethod,
        @NotNull @Min(2000) @Max(2100) Integer concernedYear,
        @NotNull @Min(1) @Max(12) Integer concernedMonth,
        LocalDate paymentDate
) {
}
