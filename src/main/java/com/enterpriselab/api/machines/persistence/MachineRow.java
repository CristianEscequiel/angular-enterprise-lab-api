package com.enterpriselab.api.machines.persistence;

/** Proyección de la consulta de máquinas con su cantidad de partes. */
record MachineRow(Long id, String code, String name, Long partCount) {
}
