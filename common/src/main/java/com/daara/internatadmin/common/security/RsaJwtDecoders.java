package com.daara.internatadmin.common.security;

import com.daara.internatadmin.common.util.RsaKeys;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Verification des jetons RS256 emis par auth-service : cle publique seule et tolerance d'horloge configuree. */
public final class RsaJwtDecoders {

    private RsaJwtDecoders() {
    }

    public static JwtDecoder create(InternatAdminSecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(RsaKeys.publicKeyFromBase64Pem(properties.jwtPublicKeyB64()))
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(properties.clockSkew())));
        return decoder;
    }
}
