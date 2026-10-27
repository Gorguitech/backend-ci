package com.daara.internatadmin.common.util;

import com.daara.internatadmin.common.support.TestJwts;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilitiesTest {

    @Test
    void matricule_formatSurQuatreChiffres() {
        assertThat(MatriculeGenerator.format("ENF", 2026, 1)).isEqualTo("ENF-2026-0001");
        assertThat(MatriculeGenerator.format("EMP", 2025, 123)).isEqualTo("EMP-2025-0123");
        assertThat(MatriculeGenerator.format("ENF", 2026, 12345)).isEqualTo("ENF-2026-12345");
        assertThat(MatriculeGenerator.yearPrefix("ENF", 2026)).isEqualTo("ENF-2026");
    }

    @Test
    void secret_longueurEtAlphabetSansCaracteresAmbigus() {
        for (int i = 0; i < 200; i++) {
            String secret = SecretGenerator.generate(10);
            assertThat(secret).hasSize(10);
            assertThat(secret).doesNotContainAnyWhitespaces();
            assertThat(secret.chars()).allMatch(c -> SecretGenerator.CHARS.indexOf(c) >= 0);
        }
        assertThat(SecretGenerator.CHARS).doesNotContain("0", "O", "1", "l", "I");
    }

    @Test
    void secret_valeursDifferentes() {
        assertThat(SecretGenerator.generate(10)).isNotEqualTo(SecretGenerator.generate(10));
    }

    @Test
    void rsa_lectureDesClesAuFormatDuScript() throws Exception {
        RSAPublicKey publicKey = RsaKeys.publicKeyFromBase64Pem(TestJwts.publicKeyB64());
        RSAPrivateKey privateKey = RsaKeys.privateKeyFromBase64Pem(TestJwts.privateKeyB64());

        assertThat(publicKey.getModulus()).isEqualTo(TestJwts.KEY.toRSAPublicKey().getModulus());
        assertThat(privateKey.getModulus()).isEqualTo(publicKey.getModulus());
    }

    @Test
    void rsa_cleAbsente() {
        assertThatThrownBy(() -> RsaKeys.publicKeyFromBase64Pem(" "))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("absente");
        assertThatThrownBy(() -> RsaKeys.privateKeyFromBase64Pem(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("absente");
    }

    @Test
    void rsa_base64Invalide() {
        assertThatThrownBy(() -> RsaKeys.publicKeyFromBase64Pem("%%%pas-du-base64%%%"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rsa_mauvaisTypeDeCle() {
        assertThatThrownBy(() -> RsaKeys.privateKeyFromBase64Pem(TestJwts.publicKeyB64()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("BEGIN PRIVATE KEY");
    }

    @Test
    void rsa_contenuCorrompu() {
        String pem = "-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----\n";
        String b64 = Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> RsaKeys.publicKeyFromBase64Pem(b64))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cle publique RSA invalide");
        String privPem = "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n";
        String privB64 = Base64.getEncoder().encodeToString(privPem.getBytes(StandardCharsets.US_ASCII));
        assertThatThrownBy(() -> RsaKeys.privateKeyFromBase64Pem(privB64))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cle privee RSA invalide");
    }
}
