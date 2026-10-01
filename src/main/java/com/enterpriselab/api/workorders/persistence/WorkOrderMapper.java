package com.enterpriselab.api.workorders.persistence;

import com.enterpriselab.api.workorders.domain.ClosingNote;
import com.enterpriselab.api.workorders.domain.MachineRef;
import com.enterpriselab.api.workorders.domain.Priority;
import com.enterpriselab.api.workorders.domain.TakenBy;
import com.enterpriselab.api.workorders.domain.WorkOrder;
import com.enterpriselab.api.workorders.domain.WorkOrderStatus;
import com.enterpriselab.api.workorders.domain.WorkOrderType;

final class WorkOrderMapper {

    private WorkOrderMapper() {
    }

    static WorkOrder toDomain(WorkOrderEntity entity) {
        MachineRef machineRef = new MachineRef(entity.getMachineId(), entity.getPartId(), entity.getBreadcrumb(),
                entity.getMachineComment());
        TakenBy takenBy = entity.getTakenById() == null ? null
                : new TakenBy(entity.getTakenById(), entity.getTakenByName(), entity.getTakenAt());
        ClosingNote closingNote = entity.getClosingAuthorId() == null ? null
                : new ClosingNote(entity.getClosingComment(), entity.getClosingAuthorId(),
                        entity.getClosingAuthorName(), entity.getClosedAt());
        return new WorkOrder(entity.getId(), entity.getTitle(), entity.getDescription(), machineRef,
                WorkOrderType.fromValue(entity.getType()), Priority.fromValue(entity.getPriority()),
                WorkOrderStatus.fromValue(entity.getStatus()), entity.getCreatedAt(), takenBy, closingNote);
    }

    static WorkOrderEntity toNewEntity(WorkOrder order) {
        MachineRef ref = order.machineRef();
        TakenBy takenBy = order.takenBy();
        ClosingNote note = order.closingNote();
        return new WorkOrderEntity(order.title(), order.description(), ref.machineId(), ref.partId(),
                ref.breadcrumb(), ref.comment(), order.type().toValue(), order.priority().toValue(),
                order.status().toValue(), order.createdAt(),
                takenBy == null ? null : takenBy.userId(), takenBy == null ? null : takenBy.name(),
                takenBy == null ? null : takenBy.at(),
                note == null ? null : note.comment(), note == null ? null : note.authorId(),
                note == null ? null : note.authorName(), note == null ? null : note.at());
    }
}
