package com.daara.internatadmin.auth.config;

import com.daara.internatadmin.auth.user.User;
import com.daara.internatadmin.common.security.JwtClaims;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Emission des jetons RS256. Les claims sont exactement ceux du monolithe (sub, userId, role,
 * mustChangePassword, iat, exp) : le frontend decode le jeton et s'appuie sur ces noms et ces types.
 */
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final Duration validity;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, Duration validity, Clock clock) {
        this.encoder = encoder;
        this.validity = validity;
        this.clock = clock;
    }

    public String generateToken(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getUsername())
                .claim(JwtClaims.USER_ID, user.getId())
                .claim(JwtClaims.ROLE, user.getRole().name())
                .claim(JwtClaims.MUST_CHANGE_PASSWORD, user.isMustChangePassword())
                .issuedAt(now)
                .expiresAt(now.plus(validity))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
