package com.enterpriselab.api.machines.persistence;

import com.enterpriselab.api.machines.domain.Part;

final class PartMapper {

    private PartMapper() {
    }

    static Part toDomain(PartEntity entity) {
        return new Part(entity.getId(), entity.getMachineId(), entity.getParentId(), entity.getName());
    }

    static PartEntity toNewEntity(Part part) {
        return new PartEntity(part.machineId(), part.parentId(), part.name());
    }
}
