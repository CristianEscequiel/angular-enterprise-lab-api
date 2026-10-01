package com.enterpriselab.api.machines.web;

import com.enterpriselab.api.machines.domain.MachineCommand;

/**
 * Cuerpo del alta y de la edición de una máquina. Sin Bean Validation a
 * propósito (mismo criterio que la spec 01): la validación la hace el dominio
 * después de autorizar, para que un rol sin permiso reciba {@code 403} y no
 * {@code 400}.
 */
public record MachineRequest(String code, String name) {

    MachineCommand toCommand() {
        return new MachineCommand(code, name);
    }
}
