package com.daara.internatadmin.personnel;

import com.daara.internatadmin.common.api.PrintActionResponse;
import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.personnel.dto.CreateEmployeRequest;
import com.daara.internatadmin.personnel.dto.EmployeResponse;
import com.daara.internatadmin.personnel.dto.UpdateEmployeRequest;
import com.daara.internatadmin.personnel.dto.UpdateEmployeStatusRequest;
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

/** Routes du monolithe ; /api/employes/stats est servi par pilotage-service (routage de la gateway). */
@RestController
@RequestMapping("/api/employes")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Personnel", description = "Enseignants et employés du Daara")
@ApiResponse(responseCode = "401", description = "Jeton expiré ou invalide",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Jeton absent", content = @Content)
public class EmployeController {

    private final EmployeService employeService;

    public EmployeController(EmployeService employeService) {
        this.employeService = employeService;
    }

    @PostMapping
    @Operation(summary = "Créer une fiche employé",
            description = "Matricule EMP-année d'embauche-séquence généré automatiquement. Un employé de même prénom, "
                    + "nom et téléphone est signalé par un 409 ; confirmDuplicate=true force la création.")
    @ApiResponse(responseCode = "201", description = "Fiche créée")
    @ApiResponse(responseCode = "400", description = "Champ invalide (salaire de base strictement positif)", content = @Content)
    @ApiResponse(responseCode = "409", description = "POSSIBLE_DUPLICATE : doublon possible, à confirmer",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<EmployeResponse> create(
            @Valid @RequestBody CreateEmployeRequest dto,
            @Parameter(description = "true pour créer malgré un doublon signalé") @RequestParam(defaultValue = "false") boolean confirmDuplicate
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeService.create(dto, confirmDuplicate));
    }

    @GetMapping
    @Operation(summary = "Lister les employés, éventuellement par statut")
    @ApiResponse(responseCode = "200", description = "Liste des employés")
    @ApiResponse(responseCode = "400", description = "Statut inconnu", content = @Content)
    public ResponseEntity<List<EmployeResponse>> listAll(@RequestParam(required = false) StatutEmploye status) {
        return ResponseEntity.ok(status != null ? employeService.listByStatus(status) : employeService.listAll());
    }

    @GetMapping("/search")
    @Operation(summary = "Rechercher par prénom ou nom (sans tenir compte de la casse)")
    @ApiResponse(responseCode = "200", description = "Employés trouvés")
    @ApiResponse(responseCode = "400", description = "Paramètre q manquant", content = @Content)
    public ResponseEntity<List<EmployeResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(employeService.search(q));
    }

    @GetMapping("/matricule/{matricule}")
    @Operation(summary = "Rechercher par matricule")
    @ApiResponse(responseCode = "200", description = "Fiche employé")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : aucun employé avec ce matricule",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<EmployeResponse> getByMatricule(@PathVariable String matricule) {
        return ResponseEntity.ok(employeService.getByMatricule(matricule));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lire une fiche employé")
    @ApiResponse(responseCode = "200", description = "Fiche employé")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : employé introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<EmployeResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(employeService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une fiche employé", description = "Matricule, date d'embauche et statut non modifiables ici.")
    @ApiResponse(responseCode = "200", description = "Fiche modifiée")
    @ApiResponse(responseCode = "400", description = "Champ invalide ou employé introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<EmployeResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateEmployeRequest dto) {
        return ResponseEntity.ok(employeService.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut (ACTIF, EN_CONGE, ANCIEN)")
    @ApiResponse(responseCode = "200", description = "Statut changé")
    @ApiResponse(responseCode = "400", description = "Statut absent ou inconnu, ou employé introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<EmployeResponse> updateStatus(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateEmployeStatusRequest dto) {
        return ResponseEntity.ok(employeService.updateStatus(id, dto.status()));
    }

    @PostMapping("/{id}/print-card")
    @Operation(summary = "Tracer l'impression de la carte professionnelle")
    @ApiResponse(responseCode = "200", description = "Impression tracée")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : employé introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PrintActionResponse> recordCardPrint(@PathVariable Long id) {
        return ResponseEntity.ok(employeService.recordCardPrint(id));
    }

    @PostMapping("/{id}/print-certificate")
    @Operation(summary = "Tracer l'impression du certificat de travail")
    @ApiResponse(responseCode = "200", description = "Impression tracée")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : employé introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PrintActionResponse> recordCertificatePrint(@PathVariable Long id) {
        return ResponseEntity.ok(employeService.recordCertificatePrint(id));
    }
}
