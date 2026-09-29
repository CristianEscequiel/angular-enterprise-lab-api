package com.enterpriselab.api.auth.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class UserTest {

    @Test
    void aTecnicoWithLegajoIsValid() {
        assertThatCode(() -> new User(1L, "tecnico", "hash", Role.TECNICO, "1001"))
                .doesNotThrowAnyException();
    }

    @Test
    void aNonTecnicoWithoutLegajoIsValid() {
        assertThatCode(() -> new User(1L, "admin", "hash", Role.ADMINISTRADOR, null))
                .doesNotThrowAnyException();
    }

    @Test
    void aTecnicoWithoutLegajoIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new User(1L, "tecnico", "hash", Role.TECNICO, null));
    }

    @Test
    void aNonTecnicoWithLegajoIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new User(1L, "admin", "hash", Role.ADMINISTRADOR, "1001"));
    }
}
