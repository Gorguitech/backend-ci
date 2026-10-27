package com.daara.internatadmin.auth.parametres;

import com.daara.internatadmin.auth.parametres.dto.ParametresResponse;
import com.daara.internatadmin.auth.parametres.dto.UpdateParametresRequest;
import com.daara.internatadmin.common.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/parametres")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Paramètres", description = "Informations et logo de l'établissement")
@ApiResponse(responseCode = "401", description = "Jeton expiré ou invalide",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Jeton absent ou rôle insuffisant", content = @Content)
public class ParametresController {

    private final ParametresService parametresService;

    public ParametresController(ParametresService parametresService) {
        this.parametresService = parametresService;
    }

    @GetMapping
    @Operation(summary = "Lire les paramètres de l'établissement")
    @ApiResponse(responseCode = "200", description = "Paramètres")
    public ResponseEntity<ParametresResponse> get() {
        return ResponseEntity.ok(parametresService.get());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier les paramètres (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Paramètres modifiés")
    @ApiResponse(responseCode = "400", description = "Nom manquant")
    public ResponseEntity<ParametresResponse> update(@Valid @RequestBody UpdateParametresRequest dto) {
        return ResponseEntity.ok(parametresService.update(dto));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Envoyer le logo (ADMIN)", description = "JPEG ou PNG, 2 Mo au maximum.")
    @ApiResponse(responseCode = "200", description = "Logo enregistré")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : fichier vide, trop gros (plus de 2 Mo) ou format refusé",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE : requête de plus de 3 Mo",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ParametresResponse> uploadLogo(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(parametresService.uploadLogo(file));
    }

    @GetMapping("/logo")
    @Operation(summary = "Lire le logo (image JPEG ou PNG)")
    @ApiResponse(responseCode = "200", description = "Image",
            content = {@Content(mediaType = "image/png"), @Content(mediaType = "image/jpeg")})
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST : aucun logo configuré",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<byte[]> getLogo() {
        byte[] logo = parametresService.getLogoBytes();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(parametresService.getLogoContentType()))
                .body(logo);
    }
}
