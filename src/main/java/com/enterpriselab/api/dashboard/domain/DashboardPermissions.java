package com.enterpriselab.api.dashboard.domain;

import com.enterpriselab.api.auth.domain.AccessPolicy;
import com.enterpriselab.api.auth.domain.Role;

/**
 * Permisos del dashboard. El resumen es la pantalla de inicio de los cuatro roles; la
 * carga por técnico solo la ven administrador y team leader (REQ-18, REQ-19).
 */
public final class DashboardPermissions {

    private DashboardPermissions() {
    }

    public static void requireSummary(Role current) {
        AccessPolicy.requireRole(current, Role.values());
    }

    public static void requireWorkload(Role current) {
        AccessPolicy.requireRole(current, Role.ADMINISTRADOR, Role.TEAM_LEADER_MANTENIMIENTO);
    }
}
