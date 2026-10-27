package com.daara.internatadmin.pilotage.stats;

import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.pilotage.stats.dto.EnfantStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Meme URL que le monolithe ; la gateway la route ici avant les autres routes /api/enfants/**. */
@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Enfants")
public class EnfantStatsController {

    private final StatsService service;

    public EnfantStatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping("/api/enfants/stats")
    @Operation(summary = "Statistiques du module élèves", description = "Effectifs par statut et suivi des paiements du mois courant.")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "401", description = "Jeton expiré ou invalide",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Jeton absent", content = @Content)
    @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE : un service dont dépend le calcul est indisponible",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<EnfantStatsResponse> getStats() {
        return ResponseEntity.ok(service.enfantStats());
    }
}
