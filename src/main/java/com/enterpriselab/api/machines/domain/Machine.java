package com.enterpriselab.api.machines.domain;

/** Máquina del maestro. {@code partCount} es de solo lectura: lo calcula la consulta, no se guarda. */
public record Machine(Long id, String code, String name, int partCount) {
}
