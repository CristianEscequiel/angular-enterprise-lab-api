package com.enterpriselab.api.workorders.persistence;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.enterpriselab.api.workorders.domain.ClosingNote;
import com.enterpriselab.api.workorders.domain.TakenBy;
import com.enterpriselab.api.workorders.domain.WorkOrder;
import com.enterpriselab.api.workorders.domain.WorkOrderStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enmienda 04-A: {@code WorkOrderMapper} es package-private, así que su prueba vive en este
 * paquete. La {@code closingNote} del dominio depende del comentario, no del autor.
 */
class WorkOrderMapperTest {

    private static final Instant AT = Instant.parse("2026-10-16T12:00:00Z");
    private static final String COMMENT = "Se reemplazó el rodamiento y se verificó el funcionamiento.";

    @Test
    void req32_req42_aRowWithAuthorAndWithoutCommentHasNoClosingNote() {
        WorkOrderEntity entity = entity("cancelled", null, null, null, 9L, "Administrador", AT);

        WorkOrder order = WorkOrderMapper.toDomain(entity);

        assertThat(order.status()).isEqualTo(WorkOrderStatus.CANCELLED);
        assertThat(order.closingNote()).isNull();
        assertThat(order.takenBy()).isNull();
    }

    @Test
    void req43_req44_aRowWithCommentHasTheCompleteClosingNote() {
        WorkOrderEntity entity = entity("completed", 5L, "Técnico", AT, 5L, "Técnico", AT, COMMENT);

        WorkOrder order = WorkOrderMapper.toDomain(entity);

        assertThat(order.closingNote()).isEqualTo(new ClosingNote(COMMENT, 5L, "Técnico", AT));
        assertThat(order.takenBy()).isEqualTo(new TakenBy(5L, "Técnico", AT));
    }

    @Test
    void req44_aRowWithoutAuthorNorCommentHasNoClosingNote() {
        WorkOrderEntity entity = entity("pending", null, null, null, null, null, null);

        assertThat(WorkOrderMapper.toDomain(entity).closingNote()).isNull();
    }

    private static WorkOrderEntity entity(String status, Long ownerId, String ownerName, Instant takenAt,
            Long authorId, String authorName, Instant closedAt) {
        return entity(status, ownerId, ownerName, takenAt, authorId, authorName, closedAt, null);
    }

    private static WorkOrderEntity entity(String status, Long ownerId, String ownerName, Instant takenAt,
            Long authorId, String authorName, Instant closedAt, String comment) {
        return new WorkOrderEntity("Orden", "Descripción de prueba", 1L, null, "M", "", "preventivo", "low", status,
                AT, ownerId, ownerName, takenAt, comment, authorId, authorName, closedAt);
    }
}
