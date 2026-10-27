package com.daara.internatadmin.auth.login;

import com.daara.internatadmin.auth.login.dto.LoginRequest;
import com.daara.internatadmin.auth.login.dto.LoginResponse;
import com.daara.internatadmin.auth.login.dto.RecoveryResetRequest;
import com.daara.internatadmin.auth.login.dto.RecoveryResetResponse;
import com.daara.internatadmin.common.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentification", description = "Connexion et récupération du compte admin (sans jeton)")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Se connecter", description = "Jeton RS256 valable 480 minutes par défaut. "
            + "Claims : sub, userId, role, mustChangePassword, iat, exp.")
    @ApiResponse(responseCode = "200", description = "Connexion réussie")
    @ApiResponse(responseCode = "400", description = "INVALID_CURRENT_PASSWORD : identifiant ou mot de passe incorrect",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "ACCOUNT_DISABLED : compte désactivé",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/reset-with-recovery-code")
    @Operation(summary = "Réinitialiser son mot de passe avec le code de récupération",
            description = "Le code est remplacé à chaque utilisation ; le nouveau est renvoyé une seule fois.")
    @ApiResponse(responseCode = "200", description = "Mot de passe réinitialisé")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST (utilisateur introuvable, aucun code) ou "
            + "INVALID_CURRENT_PASSWORD (code incorrect)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<RecoveryResetResponse> resetWithRecoveryCode(@Valid @RequestBody RecoveryResetRequest request) {
        return ResponseEntity.ok(authService.resetWithRecoveryCode(request));
    }
}
