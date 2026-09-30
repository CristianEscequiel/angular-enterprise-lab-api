package com.enterpriselab.api.maintenance.domain;

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

/**
 * La matriz coincide con {@code maintenance.permissions.ts}: técnicos
 * ver/crear/modificar → administrador y team leader; eliminar → administrador;
 * equipos → solo team leader. Un caso por rol y regla.
 */
class MaintenancePermissionsTest {

    @ParameterizedTest
    @EnumSource(Role.class)
    void techniciansReadWriteIsForAdministratorAndTeamLeader(Role role) {
        assertAllowedOnly(Set.of(ADMINISTRADOR, TEAM_LEADER_MANTENIMIENTO), role,
                () -> MaintenancePermissions.requireTechniciansReadWrite(role));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void techniciansDeleteIsForAdministratorOnly(Role role) {
        assertAllowedOnly(Set.of(ADMINISTRADOR), role,
                () -> MaintenancePermissions.requireTechniciansDelete(role));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void teamsAreForTeamLeaderOnly(Role role) {
        assertAllowedOnly(Set.of(TEAM_LEADER_MANTENIMIENTO), role,
                () -> MaintenancePermissions.requireTeams(role));
    }

    @Test
    void aMissingRoleIsAlwaysForbidden() {
        assertThatThrownBy(() -> MaintenancePermissions.requireTechniciansReadWrite(null))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> MaintenancePermissions.requireTechniciansDelete(null))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> MaintenancePermissions.requireTeams(null))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    private static void assertAllowedOnly(Set<Role> allowed, Role role, Runnable check) {
        if (allowed.contains(role)) {
            assertThatCode(check::run).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(check::run).isInstanceOf(ForbiddenOperationException.class);
        }
    }
}
