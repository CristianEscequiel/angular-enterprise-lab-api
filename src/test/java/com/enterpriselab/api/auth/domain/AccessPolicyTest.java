package com.enterpriselab.api.auth.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** REQ-11: rol no permitido lanza; rol permitido no lanza nada. */
class AccessPolicyTest {

    @Test
    void disallowedRoleThrows() {
        assertThatThrownBy(() -> AccessPolicy.requireRole(Role.TECNICO, Role.ADMINISTRADOR))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void allowedRoleDoesNotThrow() {
        assertThatCode(() -> AccessPolicy.requireRole(Role.ADMINISTRADOR, Role.ADMINISTRADOR))
                .doesNotThrowAnyException();
        assertThatCode(() -> AccessPolicy.requireRole(Role.TECNICO, Role.ADMINISTRADOR, Role.TECNICO))
                .doesNotThrowAnyException();
    }

    @Test
    void missingRoleThrows() {
        assertThatThrownBy(() -> AccessPolicy.requireRole(null, Role.ADMINISTRADOR))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
