package com.daara.internatadmin.pilotage.dashboard;

import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.pilotage.dashboard.dto.GlobalDashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Tableau de bord")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Tableau de bord global", description = "Effectifs actifs, solde global (toutes les lignes "
            + "valides) et nombre d'élèves dont la mensualité du mois courant n'est pas entièrement payée.")
    @ApiResponse(responseCode = "200", description = "Tableau de bord")
    @ApiResponse(responseCode = "401", description = "Jeton expiré ou invalide",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Jeton absent", content = @Content)
    @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE : un service dont dépend le calcul est indisponible",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<GlobalDashboardResponse> getGlobalDashboard() {
        return ResponseEntity.ok(service.getGlobalDashboard());
    }
}
