package com.daara.internatadmin.auth.login;

import com.daara.internatadmin.auth.config.JwtTokenService;
import com.daara.internatadmin.auth.login.dto.LoginRequest;
import com.daara.internatadmin.auth.login.dto.LoginResponse;
import com.daara.internatadmin.auth.login.dto.RecoveryResetRequest;
import com.daara.internatadmin.auth.login.dto.RecoveryResetResponse;
import com.daara.internatadmin.auth.user.User;
import com.daara.internatadmin.auth.user.UserRepository;
import com.daara.internatadmin.common.util.SecretGenerator;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final DaoAuthenticationProvider authenticationProvider;

    public AuthService(UserRepository userRepository, JwtTokenService jwtTokenService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        // Meme mecanisme que le monolithe : compte desactive verifie avant le mot de passe (403 ACCOUNT_DISABLED),
        // utilisateur inconnu indiscernable d'un mauvais mot de passe, protection contre l'analyse des temps de reponse.
        this.authenticationProvider = new DaoAuthenticationProvider(passwordEncoder);
        this.authenticationProvider.setUserDetailsService(this::loadUserDetails);
    }

    public LoginResponse login(LoginRequest request) {
        try {
            authenticationProvider.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Identifiant ou mot de passe incorrect.");
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Identifiant ou mot de passe incorrect."));

        return new LoginResponse(
                jwtTokenService.generateToken(user),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                user.isMustChangePassword());
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

        // rotation : le code utilise ne fonctionne plus
        String newCode = SecretGenerator.generate(10);
        user.setRecoveryCodeHash(passwordEncoder.encode(newCode));

        return new RecoveryResetResponse(
                "Mot de passe réinitialisé. Notez votre nouveau code de récupération, il ne sera plus jamais affiché.",
                newCode);
    }

    private UserDetails loadUserDetails(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur inconnu"));
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isActive())
                .authorities("ROLE_" + user.getRole().name())
                .build();
    }
}
