package com.enterpriselab.api.dashboard.domain;

import com.enterpriselab.api.workorders.domain.Priority;
import com.enterpriselab.api.workorders.domain.WorkOrderStatus;
import com.enterpriselab.api.workorders.domain.WorkOrderType;

/** Cuántas órdenes hay con esa combinación de estado, prioridad y tipo. */
public record OrderCount(WorkOrderStatus status, Priority priority, WorkOrderType type, long count) {
}
