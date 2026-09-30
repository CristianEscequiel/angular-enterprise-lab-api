package com.enterpriselab.api.maintenance.persistence;

import java.util.List;

import com.enterpriselab.api.maintenance.domain.Team;
import com.enterpriselab.api.maintenance.domain.TeamType;

/** Mapper manual entity ↔ dominio (design.md §1: sin MapStruct en esta spec). */
final class TeamMapper {

    private TeamMapper() {
    }

    static Team toDomain(TeamEntity entity, List<String> memberLegajos) {
        return new Team(entity.getId(), entity.getName(), TeamType.fromValue(entity.getType()), memberLegajos);
    }

    /** Entity nueva, sin {@code id}, para un alta. */
    static TeamEntity toNewEntity(Team team) {
        return new TeamEntity(team.name(), team.type().toValue());
    }
}
