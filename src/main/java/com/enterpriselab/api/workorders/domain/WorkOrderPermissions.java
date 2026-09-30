package com.enterpriselab.api.workorders.domain;

import com.enterpriselab.api.auth.domain.AccessPolicy;
import com.enterpriselab.api.auth.domain.ForbiddenOperationException;
import com.enterpriselab.api.auth.domain.Role;

/**
 * Matriz de permisos del módulo, espejo de {@code work-order.permissions.ts} del
 * frontend, ahora con autoridad real. Ver → cualquier rol; crear → team leader
 * ({@code preventivo}, {@code correctivo}) y producción
 * ({@code pronto-intervencion}); editar → administrador y team leader; eliminar →
 * administrador.
 */
public final class WorkOrderPermissions {

    private WorkOrderPermissions() {
    }

    /** Listado y consulta: cualquier usuario autenticado con un rol válido. */
    public static void requireView(Role current) {
        AccessPolicy.requireRole(current, Role.values());
    }

    /** Primer {@code 403} del alta: el rol no puede crear ningún tipo de orden (administrador, técnico). */
    public static void requireCreateAny(Role current) {
        AccessPolicy.requireRole(current, Role.TEAM_LEADER_MANTENIMIENTO, Role.PERSONAL_PRODUCCION);
    }

    /** Segundo {@code 403} del alta: el rol puede crear órdenes, pero no de ese tipo. */
    public static void requireCreate(Role current, WorkOrderType type) {
        if (!canCreate(current, type)) {
            throw new ForbiddenOperationException("No tenés permiso para crear una orden de tipo " + type.toValue());
        }
    }

    public static boolean canCreate(Role current, WorkOrderType type) {
        if (current == Role.TEAM_LEADER_MANTENIMIENTO) {
            return type == WorkOrderType.PREVENTIVO || type == WorkOrderType.CORRECTIVO;
        }
        if (current == Role.PERSONAL_PRODUCCION) {
            return type == WorkOrderType.PRONTO_INTERVENCION;
        }
        return false;
    }

    public static void requireEdit(Role current) {
        AccessPolicy.requireRole(current, Role.ADMINISTRADOR, Role.TEAM_LEADER_MANTENIMIENTO);
    }

    public static void requireDelete(Role current) {
        AccessPolicy.requireRole(current, Role.ADMINISTRADOR);
    }
}
