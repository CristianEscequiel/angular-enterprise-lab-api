package com.enterpriselab.api.workorders.web;

/**
 * Referencia a la máquina del alta. {@code breadcrumb} no se acepta: lo arma el
 * servidor (Jackson descarta las propiedades desconocidas, REQ-31).
 */
public record MachineRefRequest(String machineId, String partId, String comment) {
}
