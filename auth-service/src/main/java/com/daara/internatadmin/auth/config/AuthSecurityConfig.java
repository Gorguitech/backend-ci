package com.daara.internatadmin.auth.config;

import com.daara.internatadmin.common.security.InternatAdminSecurityProperties;
import com.daara.internatadmin.common.util.RsaKeys;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class AuthSecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    JwtTokenService jwtTokenService(JwtSigningProperties signing, InternatAdminSecurityProperties security, Clock clock) {
        RSAPrivateKey privateKey = RsaKeys.privateKeyFromBase64Pem(signing.privateKeyB64());
        RSAPublicKey publicKey = RsaKeys.publicKeyFromBase64Pem(security.jwtPublicKeyB64());
        requireSameKeyPair(privateKey, publicKey);

        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        return new JwtTokenService(encoder, Duration.ofMinutes(signing.expirationMinutes()), clock);
    }

    // Une paire incoherente produirait des jetons refuses par tous les services : on refuse de demarrer.
    static void requireSameKeyPair(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        boolean sameModulus = privateKey.getModulus().equals(publicKey.getModulus());
        boolean sameExponent = !(privateKey instanceof RSAPrivateCrtKey crt)
                || crt.getPublicExponent().equals(publicKey.getPublicExponent());
        if (!sameModulus || !sameExponent) {
            throw new IllegalStateException("JWT_PRIVATE_KEY_B64 et JWT_PUBLIC_KEY_B64 ne forment pas une paire");
        }
    }
}
