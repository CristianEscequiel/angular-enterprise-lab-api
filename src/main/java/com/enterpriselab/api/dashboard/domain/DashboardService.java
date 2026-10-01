package com.enterpriselab.api.dashboard.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.shared.domain.ValidationFailedException;
import com.enterpriselab.api.workorders.domain.Priority;
import com.enterpriselab.api.workorders.domain.WorkOrderStatus;
import com.enterpriselab.api.workorders.domain.WorkOrderType;

/**
 * Indicadores del tablero (spec 05). Orden de evaluación: rol ({@code 403}) → período
 * ({@code 400}). Solo lee; no hay caché, cada llamada consulta la base.
 */
@Service
public class DashboardService {

    static final String DATE_MESSAGE = "Debe ser una fecha con formato YYYY-MM-DD";
    static final String ORDER_MESSAGE = "No puede ser posterior a to";
    static final int DEFAULT_DAYS = 30;

    private final DashboardStatistics statistics;
    private final Clock clock;

    public DashboardService(DashboardStatistics statistics, Clock clock) {
        this.statistics = statistics;
        this.clock = clock;
    }

    public DashboardSummary summary(Role actor, DashboardQuery query) {
        DashboardPermissions.requireSummary(actor);
        Period period = resolvePeriod(query);

        StatisticsSnapshot snapshot = statistics.snapshot(period.fromInstant(), period.toExclusiveInstant());

        Map<WorkOrderStatus, Long> byStatus = zeros(WorkOrderStatus.class);
        Map<Priority, Long> byPriority = zeros(Priority.class);
        Map<WorkOrderType, Long> byType = zeros(WorkOrderType.class);
        long total = 0;
        for (OrderCount row : snapshot.counts()) {
            byStatus.merge(row.status(), row.count(), Long::sum);
            byPriority.merge(row.priority(), row.count(), Long::sum);
            byType.merge(row.type(), row.count(), Long::sum);
            total += row.count();
        }
        long open = byStatus.get(WorkOrderStatus.PENDING) + byStatus.get(WorkOrderStatus.IN_PROGRESS);

        ClosedStats closed = snapshot.closed();
        BigDecimal average = closed.averageResolutionMinutes() == null ? null
                : closed.averageResolutionMinutes().setScale(1, RoundingMode.HALF_UP);
        return new DashboardSummary(period, byStatus, byPriority, byType, total, open,
                new ClosedInPeriod(closed.completed(), closed.cancelled(), closed.completed() + closed.cancelled()),
                average);
    }

    /** Cantidad descendente; a igual cantidad, nombre ascendente sin distinguir mayúsculas y luego id. */
    public List<WorkloadItem> workload(Role actor) {
        DashboardPermissions.requireWorkload(actor);
        return statistics.inProgressByOwner().stream()
                .map(load -> new WorkloadItem(load.takenById(), load.takenByName(), load.count()))
                .sorted(Comparator.comparingLong(WorkloadItem::inProgress).reversed()
                        .thenComparing(WorkloadItem::takenByName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(WorkloadItem::takenById))
                .toList();
    }

    // --- Ayudas --------------------------------------------------------------------------------------

    /** Ausentes se completan (design.md §4.1); un valor presente tiene que ser una fecha real {@code YYYY-MM-DD}. */
    private Period resolvePeriod(DashboardQuery query) {
        Map<String, String> errors = new LinkedHashMap<>();
        LocalDate from = parseDate(query.from(), "from", errors);
        LocalDate to = parseDate(query.to(), "to", errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        if (to == null) {
            to = LocalDate.now(clock);
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_DAYS - 1L);
        }
        if (from.isAfter(to)) {
            throw new ValidationFailedException(Map.of("from", ORDER_MESSAGE));
        }
        return new Period(from, to);
    }

    private static LocalDate parseDate(String raw, String field, Map<String, String> errors) {
        if (raw == null) {
            return null;
        }
        if (!raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            errors.put(field, DATE_MESSAGE);
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException invalid) {
            errors.put(field, DATE_MESSAGE);
            return null;
        }
    }

    private static <E extends Enum<E>> Map<E, Long> zeros(Class<E> type) {
        Map<E, Long> counts = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            counts.put(value, 0L);
        }
        return counts;
    }
}
