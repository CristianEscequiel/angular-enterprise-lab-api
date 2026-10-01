package com.enterpriselab.api.workorders.domain;

/**
 * Criterios del listado ya validados; {@code null} significa "no filtrar por eso".
 * {@code titleText} ya viene recortado y nunca vacío.
 */
public record WorkOrderFilter(String titleText, WorkOrderStatus status, Priority priority) {
}
