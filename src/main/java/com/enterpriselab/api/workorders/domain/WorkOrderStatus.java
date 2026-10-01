package com.enterpriselab.api.workorders.domain;

import java.util.Arrays;

/**
 * Estado de una orden ({@code work-order.model.ts} del frontend). En esta spec
 * solo se lee y se filtra por él; las transiciones son de la spec 04.
 */
public enum WorkOrderStatus {

    PENDING("pending"),
    IN_PROGRESS("in-progress"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String value;

    WorkOrderStatus(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    /** Coincidencia exacta: sin recorte de espacios ni cambio de mayúsculas. */
    public static WorkOrderStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado de orden inválido: " + value));
    }
}
