package com.enterpriselab.api.workorders.web;

import com.enterpriselab.api.workorders.domain.CreateWorkOrderCommand;

/**
 * Cuerpo del alta. Sin Bean Validation a propósito (mismo criterio que las specs 01
 * y 02): la validación la hace el dominio después de autorizar, para que un rol sin
 * permiso reciba {@code 403} y no {@code 400}. {@code id}, {@code status},
 * {@code createdAt}, {@code takenBy}, {@code closingNote} y
 * {@code machineRef.breadcrumb} no existen acá: si llegan se ignoran (REQ-31).
 */
public record WorkOrderCreateRequest(String title, String description, String type, String priority,
        MachineRefRequest machineRef) {

    CreateWorkOrderCommand toCommand() {
        if (machineRef == null) {
            return new CreateWorkOrderCommand(title, description, type, priority, false, null, null, null);
        }
        return new CreateWorkOrderCommand(title, description, type, priority, true, machineRef.machineId(),
                machineRef.partId(), machineRef.comment());
    }
}
