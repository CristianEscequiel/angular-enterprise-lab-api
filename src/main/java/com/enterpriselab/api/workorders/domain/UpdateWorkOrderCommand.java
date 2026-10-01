package com.enterpriselab.api.workorders.domain;

/**
 * Entrada cruda de la edición. Solo {@code title}, {@code description} y
 * {@code priority} se aplican; {@code type} y los campos de {@code machineRef} se
 * aceptan para poder rechazar un intento de cambiarlos (REQ-33, REQ-34).
 * {@code partIdSent} distingue un {@code partId: null} enviado de uno ausente:
 * {@code null} es un valor válido de la parte, así que enviarlo a una orden con
 * parte es un intento de cambiarla.
 */
public record UpdateWorkOrderCommand(String title, String description, String priority, String type,
        String machineId, boolean partIdSent, String partId, String comment) {
}
