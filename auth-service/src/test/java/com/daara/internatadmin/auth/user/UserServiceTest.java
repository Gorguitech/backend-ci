package com.daara.internatadmin.auth.user;

import com.daara.internatadmin.auth.user.dto.ChangePasswordRequest;
import com.daara.internatadmin.auth.user.dto.ChangePasswordResponse;
import com.daara.internatadmin.auth.user.dto.CreateUserRequest;
import com.daara.internatadmin.auth.user.dto.CreateUserResponse;
import com.daara.internatadmin.auth.user.dto.UpdateUserRequest;
import com.daara.internatadmin.auth.user.dto.UserStatsResponse;
import com.daara.internatadmin.auth.user.dto.UserSummaryResponse;
import com.daara.internatadmin.common.support.TestTransactionManager;
import com.daara.internatadmin.common.tx.TransactionalRetry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    // facteur de cout minimal : les tests restent rapides, la logique est identique
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UserRepository repository = mock(UserRepository.class);
    private final TestTransactionManager transactionManager = new TestTransactionManager();
    private final UserService service = new UserService(repository, encoder, new TransactionalRetry(transactionManager));

    @Test
    void creation_usernameSansAccentsEtMotDePasseTemporaire() {
        List<User> saved = captureSaves(0);

        CreateUserResponse response = service.createGestionnaire(new CreateUserRequest("Ndèye Fatou", "N'Diaye"));

        assertThat(response.username()).isEqualTo("ndeyefatou.ndiaye");
        assertThat(response.fullName()).isEqualTo("Ndèye Fatou N'Diaye");
        assertThat(response.temporaryPassword()).hasSize(10);
        User user = saved.getFirst();
        assertThat(user.getRole()).isEqualTo(Role.GESTIONNAIRE);
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(user.isActive()).isTrue();
        assertThat(user.getPasswordHash()).isNotEqualTo(response.temporaryPassword());
        assertThat(encoder.matches(response.temporaryPassword(), user.getPasswordHash())).isTrue();
    }

    @Test
    void creation_collisionSurLeUsername_suffixeSuivantDansUneNouvelleTransaction() {
        List<User> saved = captureSaves(2);

        CreateUserResponse response = service.createGestionnaire(new CreateUserRequest("Awa", "Diop"));

        assertThat(saved).extracting(User::getUsername).containsExactly("awa.diop", "awa.diop2", "awa.diop3");
        assertThat(response.username()).isEqualTo("awa.diop3");
        assertThat(transactionManager.begins.get()).isEqualTo(3);
        assertThat(transactionManager.rollbacks.get()).isEqualTo(2);
    }

    @Test
    void creation_cinqCollisions_echecTechniqueEtJamais409() {
        captureSaves(5);
        assertThatThrownBy(() -> service.createGestionnaire(new CreateUserRequest("Awa", "Diop")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Impossible de générer un username unique après 5 tentatives");
    }

    @Test
    void reinitialisation_nouveauMotDePasseTemporaireEtChangementExige() {
        User user = user(3L, Role.GESTIONNAIRE, "ancien");
        user.setMustChangePassword(false);
        when(repository.findById(3L)).thenReturn(Optional.of(user));

        CreateUserResponse response = service.resetPassword(3L);

        assertThat(response.temporaryPassword()).hasSize(10);
        assertThat(encoder.matches(response.temporaryPassword(), user.getPasswordHash())).isTrue();
        assertThat(encoder.matches("ancien", user.getPasswordHash())).isFalse();
        assertThat(user.isMustChangePassword()).isTrue();
    }

    @Test
    void utilisateurIntrouvable_messageDuMonolithe() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resetPassword(99L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Utilisateur introuvable : 99");
        assertThatThrownBy(() -> service.activate(99L)).hasMessage("Utilisateur introuvable : 99");
        assertThatThrownBy(() -> service.getState(99L)).hasMessage("Utilisateur introuvable : 99");
    }

    @Test
    void changement_motDePasseActuelIncorrect() {
        when(repository.findById(1L)).thenReturn(Optional.of(user(1L, Role.ADMIN, "bon-mot-de-passe")));

        assertThatThrownBy(() -> service.changePassword(1L, new ChangePasswordRequest("mauvais", "nouveau-mdp")))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Mot de passe actuel incorrect");
    }

    @Test
    void changement_premierChangementDUnAdmin_codeDeRecuperationGenereUneSeuleFois() {
        User admin = user(1L, Role.ADMIN, "temporaire");
        when(repository.findById(1L)).thenReturn(Optional.of(admin));

        ChangePasswordResponse first = service.changePassword(1L, new ChangePasswordRequest("temporaire", "nouveau-mdp-1"));

        assertThat(first.recoveryCode()).hasSize(10);
        assertThat(first.message()).isEqualTo(
                "Mot de passe changé. Notez votre code de récupération, il ne sera plus jamais affiché.");
        assertThat(encoder.matches(first.recoveryCode(), admin.getRecoveryCodeHash())).isTrue();
        assertThat(admin.isMustChangePassword()).isFalse();
        assertThat(encoder.matches("nouveau-mdp-1", admin.getPasswordHash())).isTrue();

        ChangePasswordResponse second = service.changePassword(1L, new ChangePasswordRequest("nouveau-mdp-1", "nouveau-mdp-2"));
        assertThat(second.recoveryCode()).isNull();
        assertThat(second.message()).isEqualTo("Mot de passe changé avec succès.");
    }

    @Test
    void changement_gestionnaire_jamaisDeCodeDeRecuperation() {
        User gestionnaire = user(2L, Role.GESTIONNAIRE, "temporaire");
        when(repository.findById(2L)).thenReturn(Optional.of(gestionnaire));

        ChangePasswordResponse response = service.changePassword(2L, new ChangePasswordRequest("temporaire", "nouveau-mdp"));

        assertThat(response.recoveryCode()).isNull();
        assertThat(gestionnaire.getRecoveryCodeHash()).isNull();
    }

    @Test
    void changement_adminAyantDejaUnCode_pasDeNouveauCode() {
        User admin = user(1L, Role.ADMIN, "temporaire");
        admin.setRecoveryCodeHash(encoder.encode("CODEEXIST1"));
        when(repository.findById(1L)).thenReturn(Optional.of(admin));

        ChangePasswordResponse response = service.changePassword(1L, new ChangePasswordRequest("temporaire", "nouveau-mdp"));

        assertThat(response.recoveryCode()).isNull();
        assertThat(encoder.matches("CODEEXIST1", admin.getRecoveryCodeHash())).isTrue();
    }

    @Test
    void desactivation_desonPropreCompteInterdite() {
        assertThatThrownBy(() -> service.deactivate(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Vous ne pouvez pas désactiver votre propre compte");
    }

    @Test
    void desactivationPuisReactivation() {
        User user = user(2L, Role.GESTIONNAIRE, "x");
        when(repository.findById(2L)).thenReturn(Optional.of(user));

        UserSummaryResponse deactivated = service.deactivate(2L, 1L);
        assertThat(deactivated.active()).isFalse();
        assertThat(user.isActive()).isFalse();

        UserSummaryResponse activated = service.activate(2L);
        assertThat(activated.active()).isTrue();
    }

    @Test
    void modification_prenomEtNom() {
        User user = user(2L, Role.GESTIONNAIRE, "x");
        when(repository.findById(2L)).thenReturn(Optional.of(user));

        UserSummaryResponse response = service.update(2L, new UpdateUserRequest("Moussa", "Sow"));

        assertThat(response.fullName()).isEqualTo("Moussa Sow");
        assertThat(response.username()).isEqualTo("user2");
        assertThat(response.role()).isEqualTo("GESTIONNAIRE");
    }

    @Test
    void statistiquesEtEtat() {
        when(repository.count()).thenReturn(5L);
        when(repository.countByActive(true)).thenReturn(4L);
        when(repository.countByActive(false)).thenReturn(1L);
        when(repository.countByRole(Role.ADMIN)).thenReturn(1L);
        when(repository.countByRole(Role.GESTIONNAIRE)).thenReturn(4L);
        when(repository.countByMustChangePassword(true)).thenReturn(2L);

        assertThat(service.getStats()).isEqualTo(new UserStatsResponse(5, 4, 1, 1, 4, 2));

        User user = user(2L, Role.GESTIONNAIRE, "x");
        user.setActive(false);
        when(repository.findById(2L)).thenReturn(Optional.of(user));
        assertThat(service.getState(2L).active()).isFalse();
        assertThat(service.getState(2L).mustChangePassword()).isTrue();
        assertThat(service.getState(2L).role()).isEqualTo("GESTIONNAIRE");
    }

    @Test
    void liste() {
        when(repository.findAll()).thenReturn(List.of(user(1L, Role.ADMIN, "x"), user(2L, Role.GESTIONNAIRE, "y")));
        assertThat(service.listAll()).extracting(UserSummaryResponse::role).containsExactly("ADMIN", "GESTIONNAIRE");
    }

    /** Les {@code collisions} premiers enregistrements echouent sur la contrainte UNIQUE. */
    private List<User> captureSaves(int collisions) {
        List<User> saved = new ArrayList<>();
        when(repository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            saved.add(user);
            if (saved.size() <= collisions) {
                throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
            }
            return user;
        });
        return saved;
    }

    private User user(Long id, Role role, String password) {
        User user = new User();
        user.setId(id);
        user.setFirstName("Prenom" + id);
        user.setLastName("Nom" + id);
        user.setUsername("user" + id);
        user.setRole(role);
        user.setPasswordHash(encoder.encode(password));
        return user;
    }
}
