package com.enterpriselab.api.dashboard.domain;

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

class DashboardPermissionsTest {

    @ParameterizedTest
    @EnumSource(Role.class)
    void summaryIsOpenToEveryRole(Role role) {
        assertThatCode(() -> DashboardPermissions.requireSummary(role)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void workloadIsForAdministratorAndTeamLeaderOnly(Role role) {
        Set<Role> allowed = Set.of(ADMINISTRADOR, TEAM_LEADER_MANTENIMIENTO);
        if (allowed.contains(role)) {
            assertThatCode(() -> DashboardPermissions.requireWorkload(role)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> DashboardPermissions.requireWorkload(role))
                    .isInstanceOf(ForbiddenOperationException.class);
        }
    }

    @Test
    void aMissingRoleIsForbidden() {
        assertThatThrownBy(() -> DashboardPermissions.requireSummary(null))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> DashboardPermissions.requireWorkload(null))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
