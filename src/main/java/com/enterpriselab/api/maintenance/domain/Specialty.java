package com.enterpriselab.api.maintenance.domain;

import java.util.Arrays;

/**
 * Especialidad de un técnico ({@code auth.model.ts} del frontend). El valor
 * kebab-case es el que viaja en la API y se guarda en la base.
 */
public enum Specialty {

    MECANICO("mecanico"),
    ELECTRICISTA("electricista"),
    GENERAL("general");

    private final String value;

    Specialty(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    /** Coincidencia exacta: sin recorte de espacios ni cambio de mayúsculas. */
    public static Specialty fromValue(String value) {
        return Arrays.stream(values())
                .filter(specialty -> specialty.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Especialidad inválida: " + value));
    }
}
