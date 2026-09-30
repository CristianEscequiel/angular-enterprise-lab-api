package com.enterpriselab.api.workorders.domain;

/**
 * Parámetros crudos del listado. Llegan como {@code String} para que uno que no
 * es un entero (o un valor de filtro inválido) sea un {@code 400
 * VALIDATION_ERROR} y no el {@code 500} de un error de conversión (design.md §1).
 */
public record WorkOrderQuery(String page, String size, String title, String status, String priority) {
}
