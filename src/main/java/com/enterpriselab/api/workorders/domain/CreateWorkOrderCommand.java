package com.enterpriselab.api.workorders.domain;

/**
 * Entrada cruda del alta, tal como la manda el controller: todo {@code String}, sin
 * validar. La validación la hace el servicio, después de autorizar (design.md §1).
 * {@code machineRefSent} dice si vino el objeto {@code machineRef}.
 */
public record CreateWorkOrderCommand(String title, String description, String type, String priority,
        boolean machineRefSent, String machineId, String partId, String comment) {
}
