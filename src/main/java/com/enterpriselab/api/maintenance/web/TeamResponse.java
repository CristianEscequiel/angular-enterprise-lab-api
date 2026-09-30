package com.enterpriselab.api.maintenance.web;

import java.util.List;

import com.enterpriselab.api.maintenance.domain.Team;

/** Equipo tal como lo ve el cliente: {@code id} como string y los miembros por legajo, en orden de alta. */
public record TeamResponse(String id, String name, String type, List<String> memberLegajos) {

    static TeamResponse from(Team team) {
        return new TeamResponse(String.valueOf(team.id()), team.name(), team.type().toValue(),
                team.memberLegajos());
    }
}
