package com.daara.internatadmin.auth.user;

import com.daara.internatadmin.auth.user.dto.ChangePasswordRequest;
import com.daara.internatadmin.auth.user.dto.ChangePasswordResponse;
import com.daara.internatadmin.auth.user.dto.CreateUserRequest;
import com.daara.internatadmin.auth.user.dto.CreateUserResponse;
import com.daara.internatadmin.auth.user.dto.UpdateUserRequest;
import com.daara.internatadmin.auth.user.dto.UserStateResponse;
import com.daara.internatadmin.auth.user.dto.UserStatsResponse;
import com.daara.internatadmin.auth.user.dto.UserSummaryResponse;
import com.daara.internatadmin.common.audit.Audited;
import com.daara.internatadmin.common.tx.TransactionalRetry;
import com.daara.internatadmin.common.util.SecretGenerator;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Regles du monolithe. Les methodes dont la reponse contient un mot de passe temporaire ou un code
 * de recuperation (creation, reinitialisation, changement de mot de passe) ne sont volontairement pas auditees.
 */
@Service
public class UserService {

    static final int MAX_ATTEMPTS = 5;
    static final int SECRET_LENGTH = 10;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionalRetry transactionalRetry;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       TransactionalRetry transactionalRetry) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionalRetry = transactionalRetry;
    }

    /**
     * Username prenom.nom, puis prenom.nom2, prenom.nom3... en cas de collision sur la contrainte UNIQUE.
     * Chaque tentative a sa propre transaction (correction du retry inoperant du monolithe).
     */
    public CreateUserResponse createGestionnaire(CreateUserRequest dto) {
        String base = UsernameGenerator.generateBase(dto.firstName(), dto.lastName());

        return transactionalRetry.onUniqueViolation(MAX_ATTEMPTS, "username", attempt -> {
            String username = (attempt == 0) ? base : base + (attempt + 1);

            User user = new User();
            user.setFirstName(dto.firstName());
            user.setLastName(dto.lastName());
            user.setUsername(username);
            user.setRole(Role.GESTIONNAIRE);
            user.setMustChangePassword(true);

            String temporaryPassword = SecretGenerator.generate(SECRET_LENGTH);
            user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
            userRepository.saveAndFlush(user);

            return new CreateUserResponse(user.getUsername(), temporaryPassword, user.getFullName());
        });
    }

    @Transactional
    public CreateUserResponse resetPassword(Long userId) {
        User user = findUser(userId);

        String temporaryPassword = SecretGenerator.generate(SECRET_LENGTH);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);

        return new CreateUserResponse(user.getUsername(), temporaryPassword, user.getFullName());
    }

    @Transactional
    public ChangePasswordResponse changePassword(Long userId, ChangePasswordRequest dto) {
        User user = findUser(userId);

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Mot de passe actuel incorrect");
        }

        boolean wasFirstChange = user.isMustChangePassword();

        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        user.setMustChangePassword(false);

        String recoveryCode = null;
        // uniquement a la toute premiere fois, uniquement pour un ADMIN, uniquement s'il n'en a jamais eu
        if (wasFirstChange && user.getRole() == Role.ADMIN && user.getRecoveryCodeHash() == null) {
            recoveryCode = SecretGenerator.generate(SECRET_LENGTH);
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
        User user = findUser(userId);
        user.setActive(false);
        return toSummary(user);
    }

    @Audited(action = "ACTIVATE", entityType = "User")
    @Transactional
    public UserSummaryResponse activate(Long userId) {
        User user = findUser(userId);
        user.setActive(true);
        return toSummary(user);
    }

    @Audited(action = "UPDATE", entityType = "User")
    @Transactional
    public UserSummaryResponse update(Long id, UpdateUserRequest dto) {
        User user = findUser(id);
        user.setFirstName(dto.firstName());
        user.setLastName(dto.lastName());
        return toSummary(user);
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> listAll() {
        return userRepository.findAll().stream().map(UserService::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public UserStatsResponse getStats() {
        return new UserStatsResponse(
                userRepository.count(),
                userRepository.countByActive(true),
                userRepository.countByActive(false),
                userRepository.countByRole(Role.ADMIN),
                userRepository.countByRole(Role.GESTIONNAIRE),
                userRepository.countByMustChangePassword(true));
    }

    @Transactional(readOnly = true)
    public UserStateResponse getState(Long userId) {
        User user = findUser(userId);
        return new UserStateResponse(user.getId(), user.isActive(), user.isMustChangePassword(), user.getRole().name());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));
    }

    private static UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(user.getId(), user.getUsername(), user.getFullName(),
                user.getRole().name(), user.isActive(), user.isMustChangePassword());
    }
}
