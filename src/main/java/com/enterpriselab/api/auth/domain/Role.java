package com.enterpriselab.api.auth.domain;

import java.util.Arrays;

/**
 * Los cuatro roles del frontend ({@code auth.model.ts}), tal cual — no se
 * rediseñan acá (ver requirements.md). El valor kebab-case es el que viaja
 * en la base de datos y en el claim {@code role} del JWT (tarea 13).
 */
public enum Role {

    ADMINISTRADOR("administrador"),
    TEAM_LEADER_MANTENIMIENTO("team-leader-mantenimiento"),
    PERSONAL_PRODUCCION("personal-produccion"),
    TECNICO("tecnico");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static Role fromValue(String value) {
        return Arrays.stream(values())
                .filter(role -> role.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Rol inválido: " + value));
    }
}
