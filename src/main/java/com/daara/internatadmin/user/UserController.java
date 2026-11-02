package com.daara.internatadmin.user;

import com.daara.internatadmin.security.CustomUserDetails;
import com.daara.internatadmin.user.dto.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CreateUserResponse> createGestionnaire(@Valid @RequestBody CreateUserRequest dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createGestionnaire(dto));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserSummaryResponse>> listUsers() {
        return ResponseEntity.ok(userService.listAll());
    }
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CreateUserResponse> resetPassword(@PathVariable Long id) {
        return ResponseEntity.ok(userService.resetPassword(id));
    }

    @PutMapping("/me/password")
    public ResponseEntity<ChangePasswordResponse> changeMyPassword(
            @Valid @RequestBody ChangePasswordRequest dto,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(userService.changePassword(currentUser.getId(), dto));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSummaryResponse> deactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(userService.deactivate(id, currentUser.getId()));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSummaryResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(userService.activate(id));
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserSummaryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest dto
    ) {
        return ResponseEntity.ok(userService.update(id, dto));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserStatsResponse> getStats() {
        return ResponseEntity.ok(userService.getStats());
    }
}