package com.enterpriselab.api.dashboard.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import com.enterpriselab.api.dashboard.domain.DashboardSummary;

/**
 * Resumen del tablero. Las claves de los mapas son los valores kebab-case de los enums, en
 * su orden; {@code averageResolutionMinutes} sale como {@code null} explícito si no hay
 * órdenes resueltas en el período.
 */
public record DashboardSummaryResponse(PeriodResponse period, Map<String, Long> byStatus,
        Map<String, Long> byPriority, Map<String, Long> byType, long total, long open,
        ClosedInPeriodResponse closedInPeriod, Double averageResolutionMinutes) {

    static DashboardSummaryResponse from(DashboardSummary summary) {
        return new DashboardSummaryResponse(PeriodResponse.from(summary.period()),
                keyed(summary.byStatus(), status -> status.toValue()),
                keyed(summary.byPriority(), priority -> priority.toValue()),
                keyed(summary.byType(), type -> type.toValue()), summary.total(), summary.open(),
                ClosedInPeriodResponse.from(summary.closedInPeriod()),
                summary.averageResolutionMinutes() == null ? null : summary.averageResolutionMinutes().doubleValue());
    }

    private static <E> Map<String, Long> keyed(Map<E, Long> counts, Function<E, String> key) {
        Map<String, Long> keyed = new LinkedHashMap<>();
        counts.forEach((value, count) -> keyed.put(key.apply(value), count));
        return keyed;
    }
}
