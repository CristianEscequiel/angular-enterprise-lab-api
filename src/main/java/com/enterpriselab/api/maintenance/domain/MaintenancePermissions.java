package com.enterpriselab.api.maintenance.domain;

import com.enterpriselab.api.auth.domain.AccessPolicy;
import com.enterpriselab.api.auth.domain.Role;

/**
 * Matriz de permisos del módulo, espejo de {@code maintenance.permissions.ts}
 * del frontend, ahora con autoridad real. Cada método lanza
 * {@code ForbiddenOperationException} si el rol no está permitido.
 */
public final class MaintenancePermissions {

    private MaintenancePermissions() {
    }

    /** Ver, crear y modificar técnicos: administrador y team leader. */
    public static void requireTechniciansReadWrite(Role current) {
        AccessPolicy.requireRole(current, Role.ADMINISTRADOR, Role.TEAM_LEADER_MANTENIMIENTO);
    }

    /** Eliminar técnicos: solo administrador. */
    public static void requireTechniciansDelete(Role current) {
        AccessPolicy.requireRole(current, Role.ADMINISTRADOR);
    }

    /** Toda operación sobre equipos: solo team leader (el administrador tampoco accede). */
    public static void requireTeams(Role current) {
        AccessPolicy.requireRole(current, Role.TEAM_LEADER_MANTENIMIENTO);
    }
}
