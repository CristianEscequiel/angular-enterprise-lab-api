package com.enterpriselab.api.auth.security;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.auth.domain.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-7: el token que emite {@link JwtTokenIssuer}, decodificado con el
 * mismo {@link JwtDecoder} (misma clave, tarea 13), expone {@code sub},
 * {@code role}, {@code iat}/{@code exp}; {@code legajo} solo aparece para
 * un usuario {@code tecnico}.
 */
class JwtTokenIssuerTest {

    private final JwtProperties properties =
            new JwtProperties("test-only-secret-at-least-32-bytes-long!!", Duration.ofMinutes(60));
    private final JwtConfig jwtConfig = new JwtConfig();
    private final JwtEncoder jwtEncoder = jwtConfig.jwtEncoder(properties);
    private final JwtDecoder jwtDecoder = jwtConfig.jwtDecoder(properties);
    private final JwtTokenIssuer issuer = new JwtTokenIssuer(jwtEncoder, properties);

    @Test
    void issuesATokenWithSubjectRoleAndLegajoForATecnico() {
        User tecnico = new User(1L, "tecnico", "hash", Role.TECNICO, "1001");

        Jwt decoded = jwtDecoder.decode(issuer.issue(tecnico).getTokenValue());

        assertThat(decoded.getSubject()).isEqualTo("tecnico");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("tecnico");
        assertThat(decoded.getClaimAsString("legajo")).isEqualTo("1001");
        assertThat(decoded.getIssuedAt()).isNotNull();
        assertThat(decoded.getExpiresAt()).isAfter(decoded.getIssuedAt());
    }

    @Test
    void doesNotIncludeLegajoClaimForANonTecnicoUser() {
        User admin = new User(2L, "admin", "hash", Role.ADMINISTRADOR, null);

        Jwt decoded = jwtDecoder.decode(issuer.issue(admin).getTokenValue());

        assertThat(decoded.getSubject()).isEqualTo("admin");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("administrador");
        assertThat(decoded.hasClaim("legajo")).isFalse();
    }
}
