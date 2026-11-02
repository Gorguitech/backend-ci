package com.daara.internatadmin.auth;

import com.daara.internatadmin.auth.dto.LoginRequest;
import com.daara.internatadmin.auth.dto.LoginResponse;
import com.daara.internatadmin.auth.dto.RecoveryResetRequest;
import com.daara.internatadmin.auth.dto.RecoveryResetResponse;
import com.daara.internatadmin.common.SecretGenerator;
import com.daara.internatadmin.security.CustomUserDetails;
import com.daara.internatadmin.security.JwtService;
import com.daara.internatadmin.user.User;
import com.daara.internatadmin.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Identifiant ou mot de passe incorrect.");
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(); // ne peut pas arriver : l'authentification vient de réussir

        String token = jwtService.generateToken(userDetails);

        return new LoginResponse(
                token,
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                user.isMustChangePassword()
        );
    }


    @Transactional
    public RecoveryResetResponse resetWithRecoveryCode(RecoveryResetRequest dto) {
        User user = userRepository.findByUsername(dto.username())
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));

        if (user.getRecoveryCodeHash() == null) {
            throw new IllegalArgumentException("Aucun code de récupération configuré pour ce compte");
        }

        if (!passwordEncoder.matches(dto.recoveryCode(), user.getRecoveryCodeHash())) {
            throw new BadCredentialsException("Code de récupération incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        user.setMustChangePassword(false);

        String newCode = SecretGenerator.generate(10);
        user.setRecoveryCodeHash(passwordEncoder.encode(newCode));

        return new RecoveryResetResponse(
                "Mot de passe réinitialisé. Notez votre nouveau code de récupération, il ne sera plus jamais affiché.",
                newCode
        );
    }

}