package com.daara.internatadmin.auth;

import com.daara.internatadmin.auth.dto.LoginRequest;
import com.daara.internatadmin.auth.dto.LoginResponse;
import com.daara.internatadmin.auth.dto.RecoveryResetRequest;
import com.daara.internatadmin.auth.dto.RecoveryResetResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/reset-with-recovery-code")
    public ResponseEntity<RecoveryResetResponse> resetWithRecoveryCode(@Valid @RequestBody RecoveryResetRequest request) {
        return ResponseEntity.ok(authService.resetWithRecoveryCode(request));
    }
}