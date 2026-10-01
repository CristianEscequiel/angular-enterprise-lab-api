package com.enterpriselab.api.workorders.domain;

import com.enterpriselab.api.auth.domain.AccessPolicy;
import com.enterpriselab.api.auth.domain.ForbiddenOperationException;
import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.maintenance.domain.TeamType;

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

    /** Tomar una orden: solo el técnico (spec 04, REQ-3). */
    public static void requireTake(Role current) {
        AccessPolicy.requireRole(current, Role.TECNICO);
    }

    /** Cerrar una orden: solo el técnico (spec 04, REQ-15). */
    public static void requireClose(Role current) {
        AccessPolicy.requireRole(current, Role.TECNICO);
    }

    /** Liberar una orden en progreso: administrador y team leader (spec 04, REQ-23). */
    public static void requireRelease(Role current) {
        AccessPolicy.requireRole(current, Role.ADMINISTRADOR, Role.TEAM_LEADER_MANTENIMIENTO);
    }

    /** {@code guardia} atiende {@code pronto-intervencion}; {@code preventivo-correctivo}, {@code preventivo} y {@code correctivo} (REQ-4). */
    public static void requireTeamServes(TeamType team, WorkOrderType type) {
        boolean serves = team == TeamType.GUARDIA
                ? type == WorkOrderType.PRONTO_INTERVENCION
                : team == TeamType.PREVENTIVO_CORRECTIVO
                        && (type == WorkOrderType.PREVENTIVO || type == WorkOrderType.CORRECTIVO);
        if (!serves) {
            throw new ForbiddenOperationException("Tu equipo no atiende órdenes de tipo "
                    + (type == null ? "desconocido" : type.toValue()));
        }
    }
}
