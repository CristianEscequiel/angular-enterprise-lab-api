package com.enterpriselab.api.dashboard.web;

import com.enterpriselab.api.dashboard.domain.WorkloadItem;

/** Un técnico con órdenes en progreso; {@code takenById} es el id de usuario como string, igual que {@code takenBy.id}. */
public record WorkloadItemResponse(String takenById, String takenByName, long inProgress) {

    static WorkloadItemResponse from(WorkloadItem item) {
        return new WorkloadItemResponse(String.valueOf(item.takenById()), item.takenByName(), item.inProgress());
    }
}
