package com.daara.internatadmin.auth.user;

import com.daara.internatadmin.auth.user.dto.ChangePasswordRequest;
import com.daara.internatadmin.auth.user.dto.ChangePasswordResponse;
import com.daara.internatadmin.auth.user.dto.CreateUserRequest;
import com.daara.internatadmin.auth.user.dto.CreateUserResponse;
import com.daara.internatadmin.auth.user.dto.UpdateUserRequest;
import com.daara.internatadmin.auth.user.dto.UserStatsResponse;
import com.daara.internatadmin.auth.user.dto.UserSummaryResponse;
import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Utilisateurs", description = "Comptes ADMIN et GESTIONNAIRE")
@ApiResponse(responseCode = "401", description = "Jeton expiré ou invalide",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Jeton absent ou rôle insuffisant", content = @Content)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Créer un gestionnaire (ADMIN)",
            description = "Username généré (prenom.nom, suffixe numérique si déjà pris). "
                    + "Le mot de passe temporaire n'est renvoyé qu'une seule fois.")
    @ApiResponse(responseCode = "201", description = "Compte créé")
    @ApiResponse(responseCode = "400", description = "Prénom ou nom manquant")
    public ResponseEntity<CreateUserResponse> createGestionnaire(@Valid @RequestBody CreateUserRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createGestionnaire(dto));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister les comptes (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Liste des comptes")
    public ResponseEntity<List<UserSummaryResponse>> listUsers() {
        return ResponseEntity.ok(userService.listAll());
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Réinitialiser le mot de passe d'un compte (ADMIN)",
            description = "Nouveau mot de passe temporaire, changement exigé à la prochaine connexion.")
    @ApiResponse(responseCode = "200", description = "Mot de passe temporaire")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : utilisateur introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CreateUserResponse> resetPassword(@PathVariable Long id) {
        return ResponseEntity.ok(userService.resetPassword(id));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Changer son mot de passe",
            description = "Seule route accessible tant que le changement est obligatoire. Aucun nouveau jeton n'est "
                    + "renvoyé. Au premier changement d'un ADMIN, le code de récupération est renvoyé une seule fois.")
    @ApiResponse(responseCode = "200", description = "Mot de passe changé")
    @ApiResponse(responseCode = "400", description = "INVALID_CURRENT_PASSWORD ou nouveau mot de passe trop court",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ChangePasswordResponse> changeMyPassword(
            @Valid @RequestBody ChangePasswordRequest dto,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(userService.changePassword(currentUser.id(), dto));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Désactiver un compte (ADMIN)", description = "Effet immédiat, même sur un jeton encore valide.")
    @ApiResponse(responseCode = "200", description = "Compte désactivé")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : propre compte ou utilisateur introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UserSummaryResponse> deactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(userService.deactivate(id, currentUser.id()));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Réactiver un compte (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Compte réactivé")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : utilisateur introuvable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UserSummaryResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(userService.activate(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier le prénom et le nom d'un compte (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Compte modifié")
    @ApiResponse(responseCode = "400", description = "Champ manquant ou utilisateur introuvable")
    public ResponseEntity<UserSummaryResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest dto) {
        return ResponseEntity.ok(userService.update(id, dto));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Statistiques des comptes (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    public ResponseEntity<UserStatsResponse> getStats() {
        return ResponseEntity.ok(userService.getStats());
    }
}
