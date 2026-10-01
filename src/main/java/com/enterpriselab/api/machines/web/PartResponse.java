package com.enterpriselab.api.machines.web;

import com.enterpriselab.api.machines.domain.Part;

/** Parte como nodo de la lista plana del árbol: {@code parentId} es {@code null} en las de primer nivel. */
public record PartResponse(String id, String machineId, String parentId, String name) {

    static PartResponse from(Part part) {
        return new PartResponse(String.valueOf(part.id()), String.valueOf(part.machineId()),
                part.parentId() == null ? null : String.valueOf(part.parentId()), part.name());
    }
}
