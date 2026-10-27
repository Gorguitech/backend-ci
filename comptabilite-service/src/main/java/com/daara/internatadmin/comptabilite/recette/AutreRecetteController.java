package com.daara.internatadmin.comptabilite.recette;

import com.daara.internatadmin.common.api.PrintActionResponse;
import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.comptabilite.recette.dto.CreateAutreRecetteRequest;
import com.daara.internatadmin.comptabilite.recette.dto.AutreRecetteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/autres-recettes")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Autres recettes", description = "Dons, subventions et autres entrées hors paiements des élèves")
@ApiResponse(responseCode = "401", description = "Jeton expiré ou invalide",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Jeton absent", content = @Content)
public class AutreRecetteController {

    private final AutreRecetteService service;

    public AutreRecetteController(AutreRecetteService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Enregistrer une recette",
            description = "Une recette identique (source, montant, date) du même utilisateur dans les 2 minutes est "
                    + "signalée par un 409 ; confirmDuplicate=true force l'enregistrement.")
    @ApiResponse(responseCode = "201", description = "Recette enregistrée")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : champ invalide",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "POSSIBLE_DUPLICATE : doublon possible, à confirmer",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<AutreRecetteResponse> create(@Valid @RequestBody CreateAutreRecetteRequest dto,
                                                  @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto, currentUser));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Annuler une recette")
    @ApiResponse(responseCode = "200", description = "Recette annulée")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : recette introuvable ou déjà annulée",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<AutreRecetteResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancel(id));
    }

    @GetMapping
    @Operation(summary = "Recettes valides d'un mois", description = "Mois courant par défaut.")
    @ApiResponse(responseCode = "200", description = "Recettes")
    public ResponseEntity<List<AutreRecetteResponse>> list(@RequestParam(required = false) Integer year,
                                                           @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        return ResponseEntity.ok(service.list(year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue()));
    }

    @GetMapping("/{id}/receipt-data")
    @Operation(summary = "Données du reçu")
    @ApiResponse(responseCode = "200", description = "Recette")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : recette introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<AutreRecetteResponse> getReceiptData(@PathVariable Long id) {
        return ResponseEntity.ok(service.getReceiptData(id));
    }

    @PostMapping("/{id}/print-receipt")
    @Operation(summary = "Tracer l'impression du reçu")
    @ApiResponse(responseCode = "200", description = "Impression tracée")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : recette introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PrintActionResponse> recordReceiptPrint(@PathVariable Long id) {
        return ResponseEntity.ok(service.recordReceiptPrint(id));
    }
}
