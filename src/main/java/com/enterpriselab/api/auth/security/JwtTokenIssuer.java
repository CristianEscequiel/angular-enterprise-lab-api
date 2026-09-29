package com.enterpriselab.api.auth.security;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.enterpriselab.api.auth.domain.User;

/**
 * REQ-7: firma un JWT con {@code sub} (username), {@code role}, y
 * {@code legajo} solo si el usuario es {@code tecnico}, con
 * {@code iat}/{@code exp} según {@code app.jwt.ttl} (design.md §4).
 */
@Component
public class JwtTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtTokenIssuer(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }


    public Jwt issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(user.username())
                .claim("role", user.role().toValue())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.ttl()));

        if (user.legajo() != null) {
            claims.claim("legajo", user.legajo());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build()));
    }
}
