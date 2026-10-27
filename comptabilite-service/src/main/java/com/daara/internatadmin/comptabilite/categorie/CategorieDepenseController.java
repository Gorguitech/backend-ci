package com.daara.internatadmin.comptabilite.categorie;

import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.comptabilite.categorie.dto.CategorieDepenseResponse;
import com.daara.internatadmin.comptabilite.categorie.dto.CreateCategorieDepenseRequest;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories-depenses")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Catégories de dépenses")
@ApiResponse(responseCode = "401", description = "Jeton expiré ou invalide",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Jeton absent ou rôle insuffisant", content = @Content)
public class CategorieDepenseController {

    private final CategorieDepenseService service;

    public CategorieDepenseController(CategorieDepenseService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Créer une catégorie (ADMIN)")
    @ApiResponse(responseCode = "201", description = "Catégorie créée")
    @ApiResponse(responseCode = "400", description = "Nom manquant, ou INVALID_REQUEST : nom déjà utilisé",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CategorieDepenseResponse> create(@Valid @RequestBody CreateCategorieDepenseRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Désactiver une catégorie (ADMIN)", description = "Elle ne peut plus servir à de nouvelles dépenses.")
    @ApiResponse(responseCode = "200", description = "Catégorie désactivée")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : catégorie introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CategorieDepenseResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(service.deactivate(id));
    }

    @GetMapping
    @Operation(summary = "Lister les catégories")
    @ApiResponse(responseCode = "200", description = "Catégories")
    public ResponseEntity<List<CategorieDepenseResponse>> list(
            @Parameter(description = "ACTIVE (défaut), INACTIVE ou ALL") @RequestParam(defaultValue = "ACTIVE") String status) {
        return ResponseEntity.ok(service.list(status));
    }
}
