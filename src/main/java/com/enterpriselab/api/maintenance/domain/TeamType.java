package com.enterpriselab.api.maintenance.domain;

import java.util.Arrays;

/**
 * Tipo de equipo, que también es el tipo de equipo de un técnico
 * ({@code auth.model.ts} del frontend). El valor kebab-case es el que viaja en
 * la API y se guarda en la base.
 */
public enum TeamType {

    GUARDIA("guardia"),
    PREVENTIVO_CORRECTIVO("preventivo-correctivo");

    private final String value;

    TeamType(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    /** Coincidencia exacta: sin recorte de espacios ni cambio de mayúsculas. */
    public static TeamType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de equipo inválido: " + value));
    }
}
