package com.enterpriselab.api.workorders.domain;

/**
 * Dónde está la falla: la máquina, opcionalmente una parte, y el comentario.
 * {@code breadcrumb} es una foto de los nombres al crear la orden (REQ-29): no se
 * recalcula ni cambia si después se renombra o elimina la máquina o la parte
 * (REQ-30).
 */
public record MachineRef(long machineId, Long partId, String breadcrumb, String comment) {
}
