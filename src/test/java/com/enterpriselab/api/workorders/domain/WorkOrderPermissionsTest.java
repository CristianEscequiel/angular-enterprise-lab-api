package com.enterpriselab.api.workorders.domain;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.enterpriselab.api.auth.domain.ForbiddenOperationException;
import com.enterpriselab.api.auth.domain.Role;

import static com.enterpriselab.api.auth.domain.Role.ADMINISTRADOR;
import static com.enterpriselab.api.auth.domain.Role.PERSONAL_PRODUCCION;
import static com.enterpriselab.api.auth.domain.Role.TEAM_LEADER_MANTENIMIENTO;
import static com.enterpriselab.api.workorders.domain.WorkOrderType.CORRECTIVO;
import static com.enterpriselab.api.workorders.domain.WorkOrderType.PREVENTIVO;
import static com.enterpriselab.api.workorders.domain.WorkOrderType.PRONTO_INTERVENCION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La matriz coincide con {@code work-order.permissions.ts}: ver → cualquier rol;
 * crear → team leader ({@code preventivo}, {@code correctivo}) y producción
 * ({@code pronto-intervencion}); editar → administrador y team leader; eliminar →
 * administrador. Un caso por rol y regla.
 */
class WorkOrderPermissionsTest {

    @ParameterizedTest
    @EnumSource(Role.class)
    void viewIsOpenToEveryRole(Role role) {
        assertThatCode(() -> WorkOrderPermissions.requireView(role)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void createAnyIsForTeamLeaderAndProductionOnly(Role role) {
        assertAllowedOnly(Set.of(TEAM_LEADER_MANTENIMIENTO, PERSONAL_PRODUCCION), role,
                () -> WorkOrderPermissions.requireCreateAny(role));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void editIsForAdministratorAndTeamLeaderOnly(Role role) {
        assertAllowedOnly(Set.of(ADMINISTRADOR, TEAM_LEADER_MANTENIMIENTO), role,
                () -> WorkOrderPermissions.requireEdit(role));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void deleteIsForAdministratorOnly(Role role) {
        assertAllowedOnly(Set.of(ADMINISTRADOR), role, () -> WorkOrderPermissions.requireDelete(role));
    }

    /** La matriz completa de 4 roles × 3 tipos. */
    @ParameterizedTest
    @EnumSource(Role.class)
    void createPerTypeMatrix(Role role) {
        for (WorkOrderType type : WorkOrderType.values()) {
            boolean allowed = switch (role) {
                case TEAM_LEADER_MANTENIMIENTO -> type == PREVENTIVO || type == CORRECTIVO;
                case PERSONAL_PRODUCCION -> type == PRONTO_INTERVENCION;
                case ADMINISTRADOR, TECNICO -> false;
            };
            assertThat(WorkOrderPermissions.canCreate(role, type)).as("%s crea %s", role, type).isEqualTo(allowed);
            if (allowed) {
                assertThatCode(() -> WorkOrderPermissions.requireCreate(role, type)).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> WorkOrderPermissions.requireCreate(role, type))
                        .as("%s crea %s", role, type).isInstanceOf(ForbiddenOperationException.class);
            }
        }
    }

    @Test
    void theForbiddenMessageNamesTheType() {
        assertThatThrownBy(() -> WorkOrderPermissions.requireCreate(TEAM_LEADER_MANTENIMIENTO, PRONTO_INTERVENCION))
                .hasMessageContaining("pronto-intervencion");
    }

    @Test
    void aMissingRoleIsAlwaysForbidden() {
        assertThatThrownBy(() -> WorkOrderPermissions.requireView(null)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> WorkOrderPermissions.requireCreateAny(null))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> WorkOrderPermissions.requireCreate(null, PREVENTIVO))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> WorkOrderPermissions.requireEdit(null)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> WorkOrderPermissions.requireDelete(null))
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
