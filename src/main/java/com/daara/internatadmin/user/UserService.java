package com.daara.internatadmin.user;

import com.daara.internatadmin.audit.Audited;
import com.daara.internatadmin.common.SecretGenerator;
import com.daara.internatadmin.common.TransactionalRetry;
import com.daara.internatadmin.common.UsernameGenerator;
import com.daara.internatadmin.user.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionalRetry transactionalRetry;

    public CreateUserResponse createGestionnaire(CreateUserRequest dto) {
        String base = UsernameGenerator.generateBase(dto.firstName(), dto.lastName());

        // username déjà pris entre-temps : on tente le suffixe suivant, dans une nouvelle transaction
        return transactionalRetry.onUniqueViolation(MAX_ATTEMPTS, "username", attempt -> {
            String username = (attempt == 0) ? base : base + (attempt + 1);

            User user = new User();
            user.setFirstName(dto.firstName());
            user.setLastName(dto.lastName());
            user.setUsername(username);
            user.setRole(Role.GESTIONNAIRE);
            user.setMustChangePassword(true);

            String temporaryPassword = SecretGenerator.generate(10);
            user.setPasswordHash(passwordEncoder.encode(temporaryPassword));

            userRepository.saveAndFlush(user);

            // temporaryPassword renvoyé UNE SEULE FOIS ici, jamais stocké en clair, jamais relu
            return new CreateUserResponse(user.getUsername(), temporaryPassword, user.getFullName());
        });
    }

    @Transactional
    public CreateUserResponse resetPassword(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));

        String temporaryPassword = SecretGenerator.generate(10);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);

        // pas besoin de save() explicite : l'entité est déjà "managed" dans cette transaction,
        // Hibernate détecte la modification et génère l'UPDATE au commit (dirty checking)

        return new CreateUserResponse(user.getUsername(), temporaryPassword, user.getFullName());
    }


    @Transactional
    public ChangePasswordResponse changePassword(Long userId, ChangePasswordRequest dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Mot de passe actuel incorrect");
        }

        boolean wasFirstChange = user.isMustChangePassword();

        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        user.setMustChangePassword(false);

        String recoveryCode = null;
        // uniquement à la toute première fois, uniquement pour un Admin, uniquement s'il n'en a jamais eu
        if (wasFirstChange && user.getRole() == Role.ADMIN && user.getRecoveryCodeHash() == null) {
            recoveryCode = SecretGenerator.generate(10);
            user.setRecoveryCodeHash(passwordEncoder.encode(recoveryCode));
        }

        String message = (recoveryCode != null)
                ? "Mot de passe changé. Notez votre code de récupération, il ne sera plus jamais affiché."
                : "Mot de passe changé avec succès.";

        return new ChangePasswordResponse(message, recoveryCode);
    }

    @Audited(action = "DEACTIVATE", entityType = "User")
    @Transactional
    public UserSummaryResponse deactivate(Long userId, Long currentUserId) {
        if (userId.equals(currentUserId)) {
            throw new IllegalArgumentException("Vous ne pouvez pas désactiver votre propre compte");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));

        user.setActive(false);
        return toSummary(user);
    }

    @Audited(action = "ACTIVATE", entityType = "User")
    @Transactional
    public UserSummaryResponse activate(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));

        user.setActive(true);
        return toSummary(user);
    }

    public List<UserSummaryResponse> listAll() {
        return userRepository.findAll().stream()
                .map(this::toSummary)
                .toList();
    }

    private UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                user.isActive(),
                user.isMustChangePassword()
        );
    }
    @Audited(action = "UPDATE", entityType = "User")
    @Transactional
    public UserSummaryResponse update(Long id, UpdateUserRequest dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + id));

        user.setFirstName(dto.firstName());
        user.setLastName(dto.lastName());

        return toSummary(user);
    }

    public UserStatsResponse getStats() {
        long total = userRepository.count();
        long actifs = userRepository.countByActive(true);
        long desactives = userRepository.countByActive(false);
        long admins = userRepository.countByRole(Role.ADMIN);
        long gestionnaires = userRepository.countByRole(Role.GESTIONNAIRE);
        long enAttente = userRepository.countByMustChangePassword(true);

        return new UserStatsResponse(total, actifs, desactives, admins, gestionnaires, enAttente);
    }
}