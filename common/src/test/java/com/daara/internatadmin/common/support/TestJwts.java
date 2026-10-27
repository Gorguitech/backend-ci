package com.daara.internatadmin.common.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/** Paires de cles generees a l'execution et jetons signes, au meme format que ceux d'auth-service. */
public final class TestJwts {

    public static final RSAKey KEY = generate();
    public static final RSAKey OTHER_KEY = generate();

    private TestJwts() {
    }

    private static RSAKey generate() {
        try {
            return new RSAKeyGenerator(2048).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String publicKeyB64() {
        try {
            return base64Pem("PUBLIC KEY", KEY.toRSAPublicKey());
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String privateKeyB64() {
        try {
            return base64Pem("PRIVATE KEY", KEY.toRSAPrivateKey());
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** PEM (lignes de 64 caracteres, comme openssl) puis base64 sur une ligne, comme generate-jwt-keys.sh. */
    public static String base64Pem(String type, Key key) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(key.getEncoded());
        String pem = "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII));
    }

    public static String token(long userId, String username, String role) {
        return token(userId, username, role, Instant.now().plusSeconds(3600));
    }

    public static String token(long userId, String username, String role, Instant expiresAt) {
        return sign(KEY, claims(userId, username, role, expiresAt).build());
    }

    public static JWTClaimsSet.Builder claims(long userId, String username, String role, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .claim("mustChangePassword", false)
                .issueTime(Date.from(expiresAt.minusSeconds(3600)))
                .expirationTime(Date.from(expiresAt));
    }

    public static String sign(RSAKey key, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
