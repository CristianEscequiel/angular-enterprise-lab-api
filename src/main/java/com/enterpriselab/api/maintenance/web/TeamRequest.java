package com.enterpriselab.api.maintenance.web;

import java.util.List;

import com.enterpriselab.api.maintenance.domain.TeamCommand;

/**
 * Cuerpo del alta y de la edición de un equipo. Sin Bean Validation a propósito
 * (ver {@link TechnicianRequest}): la validación la hace el dominio después de
 * autorizar. En la edición reemplaza nombre, tipo y la lista completa de
 * miembros, que se validan igual que en el alta.
 */
public record TeamRequest(String name, String type, List<String> memberLegajos) {

    TeamCommand toCommand() {
        return new TeamCommand(name, type, memberLegajos);
    }
}
