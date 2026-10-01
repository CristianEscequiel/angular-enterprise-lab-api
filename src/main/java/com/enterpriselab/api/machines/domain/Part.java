package com.enterpriselab.api.machines.domain;

/**
 * Parte de una máquina, como nodo de una lista de adyacencia: {@code parentId}
 * es {@code null} en las de primer nivel. {@code machineId} y {@code parentId}
 * no cambian después del alta (REQ-25).
 */
public record Part(Long id, long machineId, Long parentId, String name) {
}
