package com.enterpriselab.api.machines.web;

import com.enterpriselab.api.machines.domain.PartPatchCommand;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de la edición de una parte. Solo {@code name} se aplica;
 * {@code machineId} y {@code parentId} se aceptan para poder rechazar un
 * intento de mover la parte (REQ-25). Es una clase y no un record porque
 * necesita distinguir un campo ausente de uno enviado como {@code null}: Jackson
 * llama al setter solo si el campo viene en el JSON.
 */
public class PartPatchRequest {

    private String name;
    private String machineId;
    private boolean machineIdSent;
    private String parentId;
    private boolean parentIdSent;

    @Schema(description = "Nuevo nombre de la parte")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Schema(description = "No se puede cambiar: si se envía, debe coincidir con la máquina actual")
    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
        this.machineIdSent = true;
    }

    @Schema(description = "No se puede cambiar: si se envía, debe coincidir con el padre actual (null en primer nivel)")
    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
        this.parentIdSent = true;
    }

    PartPatchCommand toCommand() {
        return new PartPatchCommand(name, machineIdSent, machineId, parentIdSent, parentId);
    }
}
