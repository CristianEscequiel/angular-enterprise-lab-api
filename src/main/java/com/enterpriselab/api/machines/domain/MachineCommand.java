package com.enterpriselab.api.machines.domain;

/** Entrada cruda del alta y de la edición de una máquina, tal como la manda el controller. */
public record MachineCommand(String code, String name) {
}
