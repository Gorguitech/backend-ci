package com.daara.internatadmin.comptabilite.tresorerie;

import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.comptabilite.tresorerie.dto.TresorerieResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/tresorerie")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Trésorerie")
public class TresorerieController {

    private final TresorerieService service;

    public TresorerieController(TresorerieService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Trésorerie d'un mois", description = "Mois courant par défaut. Recettes : paiements des élèves "
            + "et autres recettes ; dépenses : salaires et dépenses par catégorie.")
    @ApiResponse(responseCode = "200", description = "Trésorerie du mois")
    @ApiResponse(responseCode = "401", description = "Jeton expiré ou invalide",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Jeton absent", content = @Content)
    @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE : paiement-service indisponible",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<TresorerieResponse> getTresorerie(@RequestParam(required = false) Integer year,
                                                            @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        return ResponseEntity.ok(service.getTresorerie(year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue()));
    }
}
