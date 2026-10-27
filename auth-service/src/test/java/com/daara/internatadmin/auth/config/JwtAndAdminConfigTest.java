package com.daara.internatadmin.auth.config;

import com.daara.internatadmin.auth.user.Role;
import com.daara.internatadmin.auth.user.User;
import com.daara.internatadmin.auth.user.UserRepository;
import com.daara.internatadmin.common.security.InternatAdminSecurityProperties;
import com.daara.internatadmin.common.support.TestJwts;
import com.daara.internatadmin.common.util.RsaKeys;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAndAdminConfigTest {

    // hash calcule a l'execution : aucun hash de mot de passe versionne
    private static final String VALID_HASH = new BCryptPasswordEncoder(4).encode("mot-de-passe-de-test");

    private final Instant now = Instant.parse("2026-10-27T10:00:00Z");

    private JwtTokenService tokenService() {
        return new AuthSecurityConfig().jwtTokenService(
                new JwtSigningProperties(TestJwts.privateKeyB64(), 480),
                new InternatAdminSecurityProperties(TestJwts.publicKeyB64(), Duration.ofSeconds(60), "cle", List.of()),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void jeton_claimsIdentiquesAuMonolitheEtSignatureRs256() throws Exception {
        User user = new User();
        user.setId(7L);
        user.setUsername("awa.diop");
        user.setRole(Role.GESTIONNAIRE);
        user.setMustChangePassword(true);

        String token = tokenService().generateToken(user);

        JWSObject jws = JWSObject.parse(token);
        assertThat(jws.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        Map<String, Object> payload = jws.getPayload().toJSONObject();
        assertThat(payload.keySet()).containsExactlyInAnyOrder("sub", "userId", "role", "mustChangePassword", "iat", "exp");
        assertThat(payload.get("sub")).isEqualTo("awa.diop");
        assertThat(payload.get("userId")).isInstanceOf(Number.class);
        assertThat(((Number) payload.get("userId")).longValue()).isEqualTo(7L);
        assertThat(payload.get("role")).isEqualTo("GESTIONNAIRE");
        assertThat(payload.get("mustChangePassword")).isEqualTo(Boolean.TRUE);
        assertThat(((Number) payload.get("iat")).longValue()).isEqualTo(now.getEpochSecond());
        assertThat(((Number) payload.get("exp")).longValue()).isEqualTo(now.getEpochSecond() + 480 * 60);

        Jwt decoded = NimbusJwtDecoder.withPublicKey(RsaKeys.publicKeyFromBase64Pem(TestJwts.publicKeyB64()))
                .build().decode(token);
        assertThat(decoded.getSubject()).isEqualTo("awa.diop");
    }

    @Test
    void pairesDeClesIncoherentes_refusAuDemarrage() throws Exception {
        String otherPublic = TestJwts.base64Pem("PUBLIC KEY", new RSAKeyGenerator(2048).generate().toRSAPublicKey());
        assertThatThrownBy(() -> new AuthSecurityConfig().jwtTokenService(
                new JwtSigningProperties(TestJwts.privateKeyB64(), 480),
                new InternatAdminSecurityProperties(otherPublic, Duration.ofSeconds(60), "cle", List.of()),
                Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ne forment pas une paire");
    }

    @Test
    void clePriveeNonDefinie_refusAuDemarrage() {
        assertThatThrownBy(() -> new JwtSigningProperties("${JWT_PRIVATE_KEY_B64}", 480))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void admin_creeAuPremierDemarrageAvecLeHashFourni() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.existsByRole(Role.ADMIN)).thenReturn(false);

        new AdminAccountInitializer(repository, new AdminAccountProperties(VALID_HASH)).run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).saveAndFlush(captor.capture());
        User admin = captor.getValue();
        assertThat(admin.getUsername()).isEqualTo("admin");
        assertThat(admin.getFullName()).isEqualTo("Admin System");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(admin.getPasswordHash()).isEqualTo(VALID_HASH);
    }

    @Test
    void admin_dejaPresent_hashIgnore() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.existsByRole(Role.ADMIN)).thenReturn(true);

        new AdminAccountInitializer(repository, new AdminAccountProperties(null)).run(null);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void admin_hashAbsentOuInvalide_demarrageRefuse() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.existsByRole(Role.ADMIN)).thenReturn(false);

        for (String invalid : new String[]{null, "", "${ADMIN_INITIAL_PASSWORD_HASH}", "mot-de-passe-en-clair",
                VALID_HASH.substring(1)}) {
            assertThatThrownBy(() -> new AdminAccountInitializer(repository, new AdminAccountProperties(invalid)).run(null))
                    .as("hash refuse : %s", invalid)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ADMIN_INITIAL_PASSWORD_HASH");
        }
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void admin_creeEnMemeTempsParUneAutreInstance_sansErreur() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatCode(() -> new AdminAccountInitializer(repository, new AdminAccountProperties(VALID_HASH)).run(null))
                .doesNotThrowAnyException();
    }
}
