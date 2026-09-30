package com.enterpriselab.api.maintenance.domain;

import java.util.List;

/**
 * Equipo de mantenimiento. Los miembros se identifican por legajo y su orden es
 * el de alta (REQ-29). {@code id} es {@code null} hasta que se guarda.
 */
public record Team(Long id, String name, TeamType type, List<String> memberLegajos) {

    public Team {
        memberLegajos = List.copyOf(memberLegajos);
    }
}
