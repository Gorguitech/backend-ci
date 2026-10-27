package com.daara.internatadmin.audit.api;

import com.daara.internatadmin.audit.domain.AuditLog;
import com.daara.internatadmin.audit.domain.AuditService;
import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Audit", description = "Historique des actions tracées")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historique complet (ADMIN)",
            description = "Filtres par priorité : entityType et entityId, puis entityType seul, puis userId. "
                    + "20 éléments par page par défaut, du plus récent au plus ancien.")
    @ApiResponse(responseCode = "200", description = "Page de traces")
    @ApiResponse(responseCode = "401", description = "Jeton expiré ou invalide",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Jeton absent ou rôle différent d'ADMIN", content = @Content)
    public ResponseEntity<Page<AuditLog>> getHistory(
            @Parameter(description = "Type d'entité, par exemple Enfant") @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Long userId,
            @ParameterObject @PageableDefault(size = 20, sort = "actionDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(auditService.search(entityType, entityId, userId, pageable));
    }

    @GetMapping("/me")
    @Operation(summary = "Activité récente de l'utilisateur connecté",
            description = "10 éléments par page par défaut, du plus récent au plus ancien.")
    @ApiResponse(responseCode = "200", description = "Page de traces")
    @ApiResponse(responseCode = "401", description = "Jeton expiré ou invalide",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Jeton absent", content = @Content)
    public ResponseEntity<Page<AuditLog>> getMyRecentActivity(
            @AuthenticationPrincipal CurrentUser currentUser,
            @ParameterObject @PageableDefault(size = 10, sort = "actionDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(auditService.search(null, null, currentUser.id(), pageable));
    }
}
