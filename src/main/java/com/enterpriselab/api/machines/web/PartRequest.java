package com.enterpriselab.api.machines.web;

import com.enterpriselab.api.machines.domain.PartCommand;

/** Cuerpo del alta de una parte; {@code parentId} nulo o ausente crea una de primer nivel. */
public record PartRequest(String name, String parentId) {

    PartCommand toCommand() {
        return new PartCommand(name, parentId);
    }
}
