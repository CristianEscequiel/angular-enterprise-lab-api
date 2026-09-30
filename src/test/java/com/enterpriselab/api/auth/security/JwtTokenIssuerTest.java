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
 * un usuario {@code tecnico}. REQ-23 (enmienda 00-A): el token no lleva
 * {@code specialty} ni {@code teamType}, que pueden cambiar; la base es la fuente.
 */
class JwtTokenIssuerTest {

    private final JwtProperties properties =
            new JwtProperties("test-only-secret-at-least-32-bytes-long!!", Duration.ofMinutes(60));
    private final JwtConfig jwtConfig = new JwtConfig();
    private final JwtEncoder jwtEncoder = jwtConfig.jwtEncoder(properties);
    private final JwtDecoder jwtDecoder = jwtConfig.jwtDecoder(properties);
    private final JwtTokenIssuer issuer = new JwtTokenIssuer(jwtEncoder, properties);

    private static User tecnico() {
        return new User(1L, "tecnico", "hash", "Técnico Mecánico de Guardia", "tecnico@enterprise-lab.dev",
                Role.TECNICO, "1001", "mecanico", "guardia");
    }

    private static User admin() {
        return new User(2L, "admin", "hash", "Administrador", "admin@enterprise-lab.dev",
                Role.ADMINISTRADOR, null, null, null);
    }

    @Test
    void issuesATokenWithSubjectRoleAndLegajoForATecnico() {
        Jwt decoded = jwtDecoder.decode(issuer.issue(tecnico()).getTokenValue());

        assertThat(decoded.getSubject()).isEqualTo("tecnico");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("tecnico");
        assertThat(decoded.getClaimAsString("legajo")).isEqualTo("1001");
        assertThat(decoded.getIssuedAt()).isNotNull();
        assertThat(decoded.getExpiresAt()).isAfter(decoded.getIssuedAt());
    }

    @Test
    void doesNotIncludeLegajoClaimForANonTecnicoUser() {
        Jwt decoded = jwtDecoder.decode(issuer.issue(admin()).getTokenValue());

        assertThat(decoded.getSubject()).isEqualTo("admin");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("administrador");
        assertThat(decoded.hasClaim("legajo")).isFalse();
    }

    /** REQ-23: los claims de un técnico son exactamente los de REQ-7, sin el perfil ni los datos de contacto. */
    @Test
    void aTecnicosTokenCarriesExactlyTheRequirement7ClaimsAndNothingThatCanChange() {
        Jwt decoded = jwtDecoder.decode(issuer.issue(tecnico()).getTokenValue());

        assertThat(decoded.getClaims().keySet()).containsExactlyInAnyOrder("sub", "role", "legajo", "iat", "exp");
        assertThat(decoded.hasClaim("specialty")).isFalse();
        assertThat(decoded.hasClaim("teamType")).isFalse();
    }

    @Test
    void aNonTecnicosTokenCarriesOnlySubjectRoleAndTimes() {
        Jwt decoded = jwtDecoder.decode(issuer.issue(admin()).getTokenValue());

        assertThat(decoded.getClaims().keySet()).containsExactlyInAnyOrder("sub", "role", "iat", "exp");
    }
}
