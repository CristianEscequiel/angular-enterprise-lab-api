package com.enterpriselab.api.dashboard.domain;

import java.math.BigDecimal;
import java.util.Map;

import com.enterpriselab.api.workorders.domain.Priority;
import com.enterpriselab.api.workorders.domain.WorkOrderStatus;
import com.enterpriselab.api.workorders.domain.WorkOrderType;

/**
 * Los indicadores del tablero. Los mapas traen todas las claves del enum, en su orden,
 * con {@code 0} si no hay órdenes. {@code averageResolutionMinutes} es {@code null} si
 * ninguna orden {@code completed} se cerró en el período.
 */
public record DashboardSummary(Period period, Map<WorkOrderStatus, Long> byStatus,
        Map<Priority, Long> byPriority, Map<WorkOrderType, Long> byType, long total, long open,
        ClosedInPeriod closedInPeriod, BigDecimal averageResolutionMinutes) {
}
