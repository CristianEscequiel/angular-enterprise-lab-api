package com.enterpriselab.api.workorders.domain;

import java.util.Arrays;

/**
 * Tipo de orden ({@code work-order.model.ts} del frontend). El valor kebab-case es
 * el que viaja en la API y se guarda en la base.
 */
public enum WorkOrderType {

    PREVENTIVO("preventivo"),
    CORRECTIVO("correctivo"),
    PRONTO_INTERVENCION("pronto-intervencion");

    private final String value;

    WorkOrderType(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    /** Coincidencia exacta: sin recorte de espacios ni cambio de mayúsculas. */
    public static WorkOrderType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de orden inválido: " + value));
    }
}
