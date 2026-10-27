package com.daara.internatadmin.auth.login;

import com.daara.internatadmin.auth.config.JwtTokenService;
import com.daara.internatadmin.auth.login.dto.LoginRequest;
import com.daara.internatadmin.auth.login.dto.LoginResponse;
import com.daara.internatadmin.auth.login.dto.RecoveryResetRequest;
import com.daara.internatadmin.auth.login.dto.RecoveryResetResponse;
import com.daara.internatadmin.auth.user.Role;
import com.daara.internatadmin.auth.user.User;
import com.daara.internatadmin.auth.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UserRepository repository = mock(UserRepository.class);
    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final AuthService service = new AuthService(repository, tokens, encoder);

    @Test
    void connexionReussie_reponseDuMonolithe() {
        User admin = user("admin", Role.ADMIN, "Secret-123", true);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(tokens.generateToken(any())).thenReturn("jeton");

        LoginResponse response = service.login(new LoginRequest("admin", "Secret-123"));

        assertThat(response).isEqualTo(new LoginResponse("jeton", "admin", "Admin System", "ADMIN", true));
    }

    @Test
    void mauvaisMotDePasse_messageFrancais() {
        when(repository.findByUsername("admin")).thenReturn(Optional.of(user("admin", Role.ADMIN, "Secret-123", true)));

        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "faux")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Identifiant ou mot de passe incorrect.");
    }

    @Test
    void utilisateurInconnu_memeReponseQuUnMauvaisMotDePasse() {
        when(repository.findByUsername("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("inconnu", "x")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Identifiant ou mot de passe incorrect.");
    }

    @Test
    void compteDesactive_refuseAvantLaVerificationDuMotDePasse() {
        when(repository.findByUsername("awa.diop")).thenReturn(Optional.of(user("awa.diop", Role.GESTIONNAIRE, "Secret-123", false)));

        assertThatThrownBy(() -> service.login(new LoginRequest("awa.diop", "Secret-123")))
                .isInstanceOf(DisabledException.class);
        assertThatThrownBy(() -> service.login(new LoginRequest("awa.diop", "faux")))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    void recuperation_utilisateurInconnu() {
        when(repository.findByUsername("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resetWithRecoveryCode(new RecoveryResetRequest("x", "c", "nouveau-mdp")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Utilisateur introuvable");
    }

    @Test
    void recuperation_aucunCodeConfigure() {
        when(repository.findByUsername("awa.diop")).thenReturn(Optional.of(user("awa.diop", Role.GESTIONNAIRE, "p", true)));
        assertThatThrownBy(() -> service.resetWithRecoveryCode(new RecoveryResetRequest("awa.diop", "c", "nouveau-mdp")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Aucun code de récupération configuré pour ce compte");
    }

    @Test
    void recuperation_codeIncorrect() {
        User admin = user("admin", Role.ADMIN, "p", true);
        admin.setRecoveryCodeHash(encoder.encode("BONCODE234"));
        when(repository.findByUsername("admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.resetWithRecoveryCode(new RecoveryResetRequest("admin", "MAUVAIS", "nouveau-mdp")))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Code de récupération incorrect");
    }

    @Test
    void recuperation_reussie_motDePasseChangeEtCodeRemplace() {
        User admin = user("admin", Role.ADMIN, "ancien", true);
        admin.setRecoveryCodeHash(encoder.encode("BONCODE234"));
        when(repository.findByUsername("admin")).thenReturn(Optional.of(admin));

        RecoveryResetResponse response = service.resetWithRecoveryCode(
                new RecoveryResetRequest("admin", "BONCODE234", "nouveau-mdp"));

        assertThat(response.message()).isEqualTo(
                "Mot de passe réinitialisé. Notez votre nouveau code de récupération, il ne sera plus jamais affiché.");
        assertThat(response.newRecoveryCode()).hasSize(10);
        assertThat(encoder.matches("nouveau-mdp", admin.getPasswordHash())).isTrue();
        assertThat(admin.isMustChangePassword()).isFalse();
        assertThat(encoder.matches(response.newRecoveryCode(), admin.getRecoveryCodeHash())).isTrue();
        assertThat(encoder.matches("BONCODE234", admin.getRecoveryCodeHash())).as("ancien code invalide").isFalse();
    }

    private User user(String username, Role role, String password, boolean active) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setFirstName("Admin");
        user.setLastName("System");
        user.setRole(role);
        user.setActive(active);
        user.setPasswordHash(encoder.encode(password));
        return user;
    }
}
