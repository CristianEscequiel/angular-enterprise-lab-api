package com.enterpriselab.api.workorders.domain;

/** Entrada cruda del cierre: {@code outcome} y {@code comment} pueden ser {@code null}; los valida el servicio. */
public record CloseWorkOrderCommand(String outcome, String comment) {
}
