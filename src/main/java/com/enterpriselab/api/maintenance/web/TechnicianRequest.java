package com.enterpriselab.api.maintenance.web;

import com.enterpriselab.api.maintenance.domain.TechnicianCommand;

/**
 * Cuerpo del alta y de la edición de un técnico. Todo {@code String} y sin
 * anotaciones de Bean Validation a propósito: la validación la hace el dominio
 * después de autorizar, para que un rol sin permiso reciba {@code 403} y no
 * {@code 400} (design.md §1). En la edición {@code legajo} es opcional y, si
 * viene, tiene que coincidir con el de la URL.
 */
public record TechnicianRequest(String legajo, String firstName, String lastName, String specialty,
        String teamType) {

    TechnicianCommand toCommand() {
        return new TechnicianCommand(legajo, firstName, lastName, specialty, teamType);
    }
}
