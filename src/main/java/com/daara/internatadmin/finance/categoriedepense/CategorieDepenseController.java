package com.daara.internatadmin.finance.categoriedepense;

import com.daara.internatadmin.finance.categoriedepense.dto.CategorieDepenseResponse;
import com.daara.internatadmin.finance.categoriedepense.dto.CreateCategorieDepenseRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories-depenses")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CategorieDepenseController {

    private final CategorieDepenseService categorieDepenseService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategorieDepenseResponse> create(@Valid @RequestBody CreateCategorieDepenseRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categorieDepenseService.create(dto));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategorieDepenseResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(categorieDepenseService.deactivate(id));
    }

    @GetMapping
    public ResponseEntity<List<CategorieDepenseResponse>> listAll(
            @RequestParam(defaultValue = "ACTIVE") String status
    ) {
        return ResponseEntity.ok(categorieDepenseService.listAll(status));
    }
}