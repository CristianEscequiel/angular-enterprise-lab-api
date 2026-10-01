package com.enterpriselab.api.workorders.domain;

import java.time.Instant;

/**
 * Orden de trabajo. {@code takenBy} es {@code null} si no tiene dueño y
 * {@code closingNote} es {@code null} si no se cerró. Record sin anotaciones de
 * JPA: la persistencia lo arma en su mapper.
 */
public record WorkOrder(Long id, String title, String description, MachineRef machineRef, WorkOrderType type,
        Priority priority, WorkOrderStatus status, Instant createdAt, TakenBy takenBy, ClosingNote closingNote) {
}
