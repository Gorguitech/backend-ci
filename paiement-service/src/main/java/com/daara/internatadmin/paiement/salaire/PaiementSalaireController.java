package com.daara.internatadmin.paiement.salaire;

import com.daara.internatadmin.common.api.PrintActionResponse;
import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.paiement.salaire.dto.CreatePaiementSalaireRequest;
import com.daara.internatadmin.paiement.salaire.dto.PaiementSalaireResponse;
import com.daara.internatadmin.paiement.salaire.dto.RecuSalaireResponse;
import com.daara.internatadmin.paiement.salaire.dto.SalaireSuiviResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/paiements-salaires")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Salaires", description = "Paiement mensuel des salaires du personnel")
@ApiResponse(responseCode = "401", description = "Jeton expiré ou invalide",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Jeton absent", content = @Content)
@ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE : personnel-service indisponible",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class PaiementSalaireController {

    private final PaiementSalaireService service;

    public PaiementSalaireController(PaiementSalaireService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Enregistrer le salaire d'un mois", description = "Montant égal au salaire de base de l'employé.")
    @ApiResponse(responseCode = "201", description = "Salaire enregistré")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : employé introuvable, ou champ invalide",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "PAYMENT_ALREADY_EXISTS : salaire du mois déjà enregistré",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PaiementSalaireResponse> create(@Valid @RequestBody CreatePaiementSalaireRequest dto,
                                                          @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto, currentUser));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Annuler un paiement de salaire")
    @ApiResponse(responseCode = "200", description = "Paiement annulé")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : paiement introuvable ou déjà annulé",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PaiementSalaireResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancel(id));
    }

    @GetMapping("/{id}/receipt-data")
    @Operation(summary = "Données du bulletin de paiement")
    @ApiResponse(responseCode = "200", description = "Données du bulletin")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : paiement introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<RecuSalaireResponse> getReceiptData(@PathVariable Long id) {
        return ResponseEntity.ok(service.getReceiptData(id));
    }

    @PostMapping("/{id}/print-receipt")
    @Operation(summary = "Tracer l'impression du bulletin")
    @ApiResponse(responseCode = "200", description = "Impression tracée")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : paiement introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PrintActionResponse> recordReceiptPrint(@PathVariable Long id) {
        return ResponseEntity.ok(service.recordReceiptPrint(id));
    }

    @GetMapping("/employe/{employeId}")
    @Operation(summary = "Historique des salaires d'un employé (annulés compris)")
    @ApiResponse(responseCode = "200", description = "Paiements, du plus récent au plus ancien")
    public ResponseEntity<List<PaiementSalaireResponse>> getHistorique(@PathVariable Long employeId) {
        return ResponseEntity.ok(service.getHistorique(employeId));
    }

    @GetMapping
    @Operation(summary = "Suivi des salaires d'un mois", description = "Mois courant par défaut ; employés ACTIF et EN_CONGE.")
    @ApiResponse(responseCode = "200", description = "Une ligne par employé, triées par nom")
    public ResponseEntity<List<SalaireSuiviResponse>> getSuivi(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @Parameter(description = "ALL, PAYE ou IMPAYE") @RequestParam(defaultValue = "ALL") String status
    ) {
        LocalDate now = LocalDate.now();
        int y = (year != null) ? year : now.getYear();
        int m = (month != null) ? month : now.getMonthValue();
        return ResponseEntity.ok(service.getSuiviMensuel(y, m, status.toUpperCase()));
    }
}
