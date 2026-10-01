package com.enterpriselab.api.machines.domain;

/** Entrada cruda del alta de una parte; {@code parentId} nulo significa primer nivel. */
public record PartCommand(String name, String parentId) {
}
