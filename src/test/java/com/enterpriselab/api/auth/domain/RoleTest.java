package com.enterpriselab.api.auth.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RoleTest {

    @Test
    void resolvesEachOfTheFourValidValues() {
        assertThat(Role.fromValue("administrador")).isEqualTo(Role.ADMINISTRADOR);
        assertThat(Role.fromValue("team-leader-mantenimiento")).isEqualTo(Role.TEAM_LEADER_MANTENIMIENTO);
        assertThat(Role.fromValue("personal-produccion")).isEqualTo(Role.PERSONAL_PRODUCCION);
        assertThat(Role.fromValue("tecnico")).isEqualTo(Role.TECNICO);
    }

    @Test
    void toValueRoundTripsBackToTheSameRole() {
        for (Role role : Role.values()) {
            assertThat(Role.fromValue(role.toValue())).isEqualTo(role);
        }
    }

    @Test
    void rejectsAValueOutsideTheFourValidOnes() {
        assertThatIllegalArgumentException().isThrownBy(() -> Role.fromValue("rol-inventado"));
    }
}
