package com.enterpriselab.api.workorders.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code machineRef} de la edición. Solo se acepta para poder rechazar un intento
 * de cambiarlo (REQ-34). Es una clase y no un record porque necesita distinguir un
 * {@code partId} ausente de uno enviado como {@code null}: Jackson llama al setter
 * solo si el campo viene en el JSON.
 */
public class MachineRefPatch {

    private String machineId;
    private String partId;
    private boolean partIdSent;
    private String comment;

    @Schema(description = "No se puede cambiar: si se envía, debe coincidir con la máquina actual")
    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    @Schema(description = "No se puede cambiar: si se envía (aunque sea null), debe coincidir con la parte actual")
    public String getPartId() {
        return partId;
    }

    public void setPartId(String partId) {
        this.partId = partId;
        this.partIdSent = true;
    }

    @Schema(description = "No se puede cambiar: si se envía, debe coincidir con el comentario actual")
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    boolean isPartIdSent() {
        return partIdSent;
    }
}
