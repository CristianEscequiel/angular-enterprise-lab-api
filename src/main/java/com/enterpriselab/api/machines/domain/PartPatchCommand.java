package com.enterpriselab.api.machines.domain;

/**
 * Entrada cruda de la edición de una parte. Además de los valores, dice si el
 * cliente envió {@code machineId} y {@code parentId}: un {@code parentId: null}
 * enviado a una sub-parte es un intento de moverla a primer nivel, distinto de
 * no enviarlo (REQ-25).
 */
public record PartPatchCommand(String name, boolean machineIdSent, String machineId,
        boolean parentIdSent, String parentId) {
}
