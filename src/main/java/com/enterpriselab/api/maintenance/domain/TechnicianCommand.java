package com.enterpriselab.api.maintenance.domain;

/**
 * Entrada cruda de alta y edición de un técnico, tal como llega del controller:
 * todo {@code String}, sin validar. La validación la hace el servicio, después
 * de autorizar. En la edición {@code legajo} es opcional.
 */
public record TechnicianCommand(String legajo, String firstName, String lastName,
        String specialty, String teamType) {
}
