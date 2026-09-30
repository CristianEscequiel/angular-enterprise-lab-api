package com.enterpriselab.api.workorders.domain;

import java.util.Arrays;

/** Prioridad de una orden ({@code work-order.model.ts} del frontend). */
public enum Priority {

    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String value;

    Priority(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    /** Coincidencia exacta: sin recorte de espacios ni cambio de mayúsculas. */
    public static Priority fromValue(String value) {
        return Arrays.stream(values())
                .filter(priority -> priority.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Prioridad inválida: " + value));
    }
}
