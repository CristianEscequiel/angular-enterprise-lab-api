package com.enterpriselab.api.workorders.web;

import com.enterpriselab.api.workorders.domain.UpdateWorkOrderCommand;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de la edición. Solo {@code title}, {@code description} y {@code priority}
 * se aplican; {@code type} y {@code machineRef} se aceptan para poder rechazar un
 * intento de cambiarlos (REQ-33, REQ-34). Los demás campos de la orden
 * ({@code id}, {@code status}, {@code takenBy}, {@code closingNote},
 * {@code createdAt}, {@code machineRef.breadcrumb}) se ignoran (REQ-35), así que el
 * cliente puede mandar la orden completa.
 */
public class WorkOrderUpdateRequest {

    private String title;
    private String description;
    private String priority;
    private String type;
    private MachineRefPatch machineRef;

    @Schema(description = "Nuevo título, de 3 a 150 caracteres")
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Schema(description = "Nueva descripción, de 10 a 2000 caracteres")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Schema(description = "Nueva prioridad: low, medium o high")
    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    @Schema(description = "No se puede cambiar: si se envía, debe coincidir con el tipo actual")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public MachineRefPatch getMachineRef() {
        return machineRef;
    }

    public void setMachineRef(MachineRefPatch machineRef) {
        this.machineRef = machineRef;
    }

    UpdateWorkOrderCommand toCommand() {
        if (machineRef == null) {
            return new UpdateWorkOrderCommand(title, description, priority, type, null, false, null, null);
        }
        return new UpdateWorkOrderCommand(title, description, priority, type, machineRef.getMachineId(),
                machineRef.isPartIdSent(), machineRef.getPartId(), machineRef.getComment());
    }
}
