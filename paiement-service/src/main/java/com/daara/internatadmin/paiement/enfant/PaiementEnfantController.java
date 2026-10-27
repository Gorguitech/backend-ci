package com.daara.internatadmin.paiement.enfant;

import com.daara.internatadmin.common.api.PrintActionResponse;
import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.paiement.enfant.dto.CreatePaiementEnfantRequest;
import com.daara.internatadmin.paiement.enfant.dto.InscriptionSuiviResponse;
import com.daara.internatadmin.paiement.enfant.dto.PaiementEnfantResponse;
import com.daara.internatadmin.paiement.enfant.dto.RecuPaiementResponse;
import com.daara.internatadmin.paiement.enfant.dto.SuiviMensuelResponse;
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
@RequestMapping("/api/paiements-enfants")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Paiements des élèves", description = "Inscriptions et mensualités")
@ApiResponse(responseCode = "401", description = "Jeton expiré ou invalide",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Jeton absent", content = @Content)
@ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE : enfant-service indisponible ou verrou non obtenu",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class PaiementEnfantController {

    private final PaiementEnfantService service;

    public PaiementEnfantController(PaiementEnfantService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Enregistrer un paiement (inscription ou mensualité)",
            description = "Inscription : montant exactement égal aux frais d'inscription. Mensualité : montant au plus "
                    + "égal au restant dû ; un montant inférieur exige confirmPartial=true. Le montant dû est figé "
                    + "au premier paiement du mois.")
    @ApiResponse(responseCode = "201", description = "Paiement enregistré")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : élève introuvable, année ou mois manquant",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "AMOUNT_MISMATCH, PARTIAL_PAYMENT_NOT_CONFIRMED ou PAYMENT_ALREADY_EXISTS",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PaiementEnfantResponse> create(@Valid @RequestBody CreatePaiementEnfantRequest dto,
                                                         @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto, currentUser));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Annuler un paiement", description = "Le paiement passe au statut ANNULE, il n'est jamais supprimé.")
    @ApiResponse(responseCode = "200", description = "Paiement annulé")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : paiement introuvable ou déjà annulé",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PaiementEnfantResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancel(id));
    }

    @GetMapping("/{id}/receipt-data")
    @Operation(summary = "Données du reçu")
    @ApiResponse(responseCode = "200", description = "Données du reçu")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : paiement introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<RecuPaiementResponse> getReceiptData(@PathVariable Long id) {
        return ResponseEntity.ok(service.getReceiptData(id));
    }

    @PostMapping("/{id}/print-receipt")
    @Operation(summary = "Tracer l'impression du reçu")
    @ApiResponse(responseCode = "200", description = "Impression tracée")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : paiement introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PrintActionResponse> recordReceiptPrint(@PathVariable Long id) {
        return ResponseEntity.ok(service.recordReceiptPrint(id));
    }

    @GetMapping("/enfant/{enfantId}/inscription")
    @Operation(summary = "Historique des paiements d'inscription d'un élève (annulés compris)")
    @ApiResponse(responseCode = "200", description = "Paiements, du plus récent au plus ancien")
    public ResponseEntity<List<PaiementEnfantResponse>> getHistoriqueInscription(@PathVariable Long enfantId) {
        return ResponseEntity.ok(service.getHistorique(enfantId, TypePaiementEnfant.INSCRIPTION));
    }

    @GetMapping("/enfant/{enfantId}/mensualites")
    @Operation(summary = "Historique des mensualités d'un élève (annulées comprises)")
    @ApiResponse(responseCode = "200", description = "Paiements, du plus récent au plus ancien")
    public ResponseEntity<List<PaiementEnfantResponse>> getHistoriqueMensualites(@PathVariable Long enfantId) {
        return ResponseEntity.ok(service.getHistorique(enfantId, TypePaiementEnfant.MENSUALITE));
    }

    @GetMapping("/mensualites")
    @Operation(summary = "Suivi des mensualités d'un mois", description = "Mois courant par défaut ; élèves ACTIF et EN_VACANCES.")
    @ApiResponse(responseCode = "200", description = "Une ligne par élève, triées par nom")
    public ResponseEntity<List<SuiviMensuelResponse>> getMensualites(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @Parameter(description = "ALL, PAYE, PARTIEL ou IMPAYE") @RequestParam(defaultValue = "ALL") String status
    ) {
        LocalDate now = LocalDate.now();
        int y = (year != null) ? year : now.getYear();
        int m = (month != null) ? month : now.getMonthValue();
        return ResponseEntity.ok(service.getSuiviMensuel(y, m, status.toUpperCase()));
    }

    @GetMapping("/inscriptions")
    @Operation(summary = "Suivi des inscriptions", description = "Élèves ACTIF et EN_VACANCES.")
    @ApiResponse(responseCode = "200", description = "Une ligne par élève")
    public ResponseEntity<List<InscriptionSuiviResponse>> getInscriptions(
            @Parameter(description = "ALL, PAYE ou IMPAYE") @RequestParam(defaultValue = "ALL") String status) {
        return ResponseEntity.ok(service.getInscriptions(status.toUpperCase()));
    }
}
