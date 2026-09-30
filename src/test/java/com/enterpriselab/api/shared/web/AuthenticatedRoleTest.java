package com.enterpriselab.api.shared.web;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.oauth2.jwt.Jwt;

import com.enterpriselab.api.auth.domain.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AuthenticatedRoleTest {

    @ParameterizedTest
    @EnumSource(Role.class)
    void resolvesEachOfTheFourRolesFromTheRoleClaim(Role role) {
        assertThat(AuthenticatedRole.from(jwtWithRole(role.toValue()))).isEqualTo(role);
    }

    @Test
    void anUnknownRoleClaimIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> AuthenticatedRole.from(jwtWithRole("rol-inventado")));
    }

    private static Jwt jwtWithRole(String role) {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject("alguien").claim("role", role)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
