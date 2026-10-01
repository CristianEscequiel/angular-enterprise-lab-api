package com.enterpriselab.api.machines.domain;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.enterpriselab.api.auth.domain.ForbiddenOperationException;
import com.enterpriselab.api.auth.domain.Role;

import static com.enterpriselab.api.auth.domain.Role.ADMINISTRADOR;
import static com.enterpriselab.api.auth.domain.Role.TEAM_LEADER_MANTENIMIENTO;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** REQ-31 y REQ-32: lectura para los cuatro roles, escritura solo para administrador y team leader. */
class MachinesPermissionsTest {

    @ParameterizedTest
    @EnumSource(Role.class)
    void readIsOpenToEveryRole(Role role) {
        assertThatCode(() -> MachinesPermissions.requireRead(role)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void writeIsForAdministratorAndTeamLeaderOnly(Role role) {
        if (Set.of(ADMINISTRADOR, TEAM_LEADER_MANTENIMIENTO).contains(role)) {
            assertThatCode(() -> MachinesPermissions.requireWrite(role)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> MachinesPermissions.requireWrite(role))
                    .isInstanceOf(ForbiddenOperationException.class);
        }
    }

    @Test
    void aMissingRoleIsForbiddenForReadAndWrite() {
        assertThatThrownBy(() -> MachinesPermissions.requireRead(null))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> MachinesPermissions.requireWrite(null))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
