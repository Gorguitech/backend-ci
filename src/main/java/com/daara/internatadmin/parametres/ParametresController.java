package com.daara.internatadmin.parametres;

import com.daara.internatadmin.parametres.dto.ParametresResponse;
import com.daara.internatadmin.parametres.dto.UpdateParametresRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/parametres")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ParametresController {

    private final ParametresService parametresService;

    @GetMapping
    public ResponseEntity<ParametresResponse> get() {
        return ResponseEntity.ok(parametresService.get());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ParametresResponse> update(@Valid @RequestBody UpdateParametresRequest dto) {
        return ResponseEntity.ok(parametresService.update(dto));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ParametresResponse> uploadLogo(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(parametresService.uploadLogo(file));
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> getLogo() {
        byte[] logo = parametresService.getLogoBytes();
        String contentType = parametresService.getLogoContentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(logo);
    }
}