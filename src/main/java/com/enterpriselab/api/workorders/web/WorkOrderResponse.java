package com.enterpriselab.api.workorders.web;

import java.time.Instant;

import com.enterpriselab.api.workorders.domain.WorkOrder;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Orden tal como la ve el cliente: {@code id} como string, enums en su valor
 * kebab-case y {@code createdAt} en ISO-8601 UTC. {@code takenBy} y
 * {@code machineRef.partId} se serializan como {@code null} explícito;
 * {@code closingNote} directamente <em>falta</em> si la orden no se cerró.
 */
public record WorkOrderResponse(String id, String title, String description, MachineRefResponse machineRef,
        String type, String priority, String status, Instant createdAt, TakenByResponse takenBy,
        @JsonInclude(JsonInclude.Include.NON_NULL) ClosingNoteResponse closingNote) {

    static WorkOrderResponse from(WorkOrder order) {
        return new WorkOrderResponse(String.valueOf(order.id()), order.title(), order.description(),
                MachineRefResponse.from(order.machineRef()), order.type().toValue(), order.priority().toValue(),
                order.status().toValue(), order.createdAt(),
                order.takenBy() == null ? null : TakenByResponse.from(order.takenBy()),
                order.closingNote() == null ? null : ClosingNoteResponse.from(order.closingNote()));
    }
}
