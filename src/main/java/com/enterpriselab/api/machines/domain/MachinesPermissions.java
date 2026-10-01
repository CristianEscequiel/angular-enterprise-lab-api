package com.enterpriselab.api.machines.domain;

import com.enterpriselab.api.auth.domain.AccessPolicy;
import com.enterpriselab.api.auth.domain.Role;

/**
 * Matriz de permisos del módulo. El frontend gestiona el maestro solo con
 * administrador y team leader ({@code canManageMachines}), pero producción y
 * técnico también tienen que leerlo para elegir máquina y parte al crear una
 * orden (spec 03): la lectura queda abierta a los cuatro roles.
 */
public final class MachinesPermissions {

    private MachinesPermissions() {
    }

    /** Lectura: cualquier usuario autenticado con un rol válido. */
    public static void requireRead(Role current) {
        AccessPolicy.requireRole(current, Role.values());
    }

    /** Alta, edición y baja de máquinas y partes: administrador y team leader. */
    public static void requireWrite(Role current) {
        AccessPolicy.requireRole(current, Role.ADMINISTRADOR, Role.TEAM_LEADER_MANTENIMIENTO);
    }
}
