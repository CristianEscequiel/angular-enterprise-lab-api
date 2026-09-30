package com.enterpriselab.api.machines.web;

import com.enterpriselab.api.machines.domain.Machine;

/** Máquina tal como la ve el cliente: {@code id} como string y la cantidad de partes que tiene. */
public record MachineResponse(String id, String code, String name, int partCount) {

    static MachineResponse from(Machine machine) {
        return new MachineResponse(String.valueOf(machine.id()), machine.code(), machine.name(),
                machine.partCount());
    }
}
