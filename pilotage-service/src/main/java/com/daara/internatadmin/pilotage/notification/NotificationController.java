package com.daara.internatadmin.pilotage.notification;

import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.pilotage.notification.dto.NotificationItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Notifications de l'utilisateur connecté", description = "Mensualités impayées ou partielles, "
            + "inscriptions et salaires impayés pour tous ; solde négatif et comptes désactivés pour l'ADMIN seulement.")
    @ApiResponse(responseCode = "200", description = "Notifications, liste vide s'il n'y a rien à signaler")
    @ApiResponse(responseCode = "401", description = "Jeton expiré ou invalide",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Jeton absent", content = @Content)
    @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE : un service dont dépend le calcul est indisponible",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<List<NotificationItem>> getNotifications(@Parameter(hidden = true) @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(service.getNotifications(currentUser));
    }
}
