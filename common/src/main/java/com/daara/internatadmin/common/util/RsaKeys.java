package com.daara.internatadmin.common.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Lecture des cles RSA fournies par variable d'environnement : un PEM complet, lui-meme encode en base64
 * sur une seule ligne (format produit par scripts/generate-jwt-keys.sh).
 */
public final class RsaKeys {

    private RsaKeys() {
    }

    public static RSAPublicKey publicKeyFromBase64Pem(String base64Pem) {
        byte[] der = pemBody(base64Pem, "PUBLIC KEY");
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException("Cle publique RSA invalide", e);
        }
    }

    public static RSAPrivateKey privateKeyFromBase64Pem(String base64Pem) {
        byte[] der = pemBody(base64Pem, "PRIVATE KEY");
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException("Cle privee RSA invalide (PKCS#8 attendu)", e);
        }
    }

    private static byte[] pemBody(String base64Pem, String type) {
        if (base64Pem == null || base64Pem.isBlank()) {
            throw new IllegalStateException("Cle RSA absente (" + type + ")");
        }
        String pem;
        try {
            pem = new String(Base64.getMimeDecoder().decode(base64Pem.trim()), StandardCharsets.US_ASCII);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Cle RSA mal encodee en base64 (" + type + ")", e);
        }
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        int start = pem.indexOf(begin);
        int stop = pem.indexOf(end);
        if (start < 0 || stop < start) {
            throw new IllegalStateException("En-tete PEM attendu : " + begin);
        }
        String body = pem.substring(start + begin.length(), stop).replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
