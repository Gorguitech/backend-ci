package com.daara.internatadmin.enfant;

import com.daara.internatadmin.common.api.PrintActionResponse;
import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.enfant.dto.CreateEnfantRequest;
import com.daara.internatadmin.enfant.dto.EnfantResponse;
import com.daara.internatadmin.enfant.dto.UpdateEnfantRequest;
import com.daara.internatadmin.enfant.dto.UpdateStatusRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Routes du monolithe ; /api/enfants/stats est servi par pilotage-service (routage de la gateway). */
@RestController
@RequestMapping("/api/enfants")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Élèves", description = "Fiches des talibés")
@ApiResponse(responseCode = "401", description = "Jeton expiré ou invalide",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Jeton absent", content = @Content)
public class EnfantController {

    private final EnfantService enfantService;

    public EnfantController(EnfantService enfantService) {
        this.enfantService = enfantService;
    }

    @PostMapping
    @Operation(summary = "Créer une fiche élève",
            description = "Matricule ENF-année d'entrée-séquence généré automatiquement. Un élève de même prénom, nom "
                    + "et téléphone de tuteur est signalé par un 409 ; confirmDuplicate=true force la création.")
    @ApiResponse(responseCode = "201", description = "Fiche créée")
    @ApiResponse(responseCode = "400", description = "Champ invalide, ou ni date de naissance ni âge approximatif",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "POSSIBLE_DUPLICATE : doublon possible, à confirmer",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<EnfantResponse> create(
            @Valid @RequestBody CreateEnfantRequest dto,
            @Parameter(description = "true pour créer malgré un doublon signalé") @RequestParam(defaultValue = "false") boolean confirmDuplicate
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enfantService.create(dto, confirmDuplicate));
    }

    @GetMapping
    @Operation(summary = "Lister les élèves, éventuellement par statut")
    @ApiResponse(responseCode = "200", description = "Liste des élèves")
    @ApiResponse(responseCode = "400", description = "Statut inconnu", content = @Content)
    public ResponseEntity<List<EnfantResponse>> listAll(@RequestParam(required = false) StatutEnfant status) {
        return ResponseEntity.ok(status != null ? enfantService.listByStatus(status) : enfantService.listAll());
    }

    @GetMapping("/search")
    @Operation(summary = "Rechercher par prénom ou nom (sans tenir compte de la casse)")
    @ApiResponse(responseCode = "200", description = "Élèves trouvés")
    @ApiResponse(responseCode = "400", description = "Paramètre q manquant", content = @Content)
    public ResponseEntity<List<EnfantResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(enfantService.search(q));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lire une fiche élève")
    @ApiResponse(responseCode = "200", description = "Fiche élève")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : élève introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<EnfantResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(enfantService.getById(id));
    }

    @GetMapping("/matricule/{matricule}")
    @Operation(summary = "Rechercher par matricule")
    @ApiResponse(responseCode = "200", description = "Fiche élève")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : aucun élève avec ce matricule",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<EnfantResponse> getByMatricule(@PathVariable String matricule) {
        return ResponseEntity.ok(enfantService.getByMatricule(matricule));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une fiche élève", description = "Matricule, date d'entrée et statut non modifiables ici.")
    @ApiResponse(responseCode = "200", description = "Fiche modifiée")
    @ApiResponse(responseCode = "400", description = "Champ invalide, élève introuvable, ou ni date de naissance ni âge",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<EnfantResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateEnfantRequest dto) {
        return ResponseEntity.ok(enfantService.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut (ACTIF, SORTI, EN_VACANCES)")
    @ApiResponse(responseCode = "200", description = "Statut changé")
    @ApiResponse(responseCode = "400", description = "Statut absent ou inconnu, ou élève introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<EnfantResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest dto) {
        return ResponseEntity.ok(enfantService.updateStatus(id, dto.status()));
    }

    @PostMapping("/{id}/print-card")
    @Operation(summary = "Tracer l'impression de la carte d'identité scolaire")
    @ApiResponse(responseCode = "200", description = "Impression tracée")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : élève introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PrintActionResponse> recordCardPrint(@PathVariable Long id) {
        return ResponseEntity.ok(enfantService.recordCardPrint(id));
    }

    @PostMapping("/{id}/print-certificate")
    @Operation(summary = "Tracer l'impression du certificat d'inscription")
    @ApiResponse(responseCode = "200", description = "Impression tracée")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : élève introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PrintActionResponse> recordCertificatePrint(@PathVariable Long id) {
        return ResponseEntity.ok(enfantService.recordCertificatePrint(id));
    }
}
