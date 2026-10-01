package com.enterpriselab.api.machines.persistence;

import com.enterpriselab.api.machines.domain.Machine;

final class MachineMapper {

    private MachineMapper() {
    }

    static Machine toDomain(MachineRow row) {
        return new Machine(row.id(), row.code(), row.name(), Math.toIntExact(row.partCount()));
    }

    static MachineEntity toNewEntity(Machine machine) {
        return new MachineEntity(machine.code(), machine.name());
    }
}
