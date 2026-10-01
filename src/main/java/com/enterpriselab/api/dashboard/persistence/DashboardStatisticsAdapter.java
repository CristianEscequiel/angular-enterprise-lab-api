package com.enterpriselab.api.dashboard.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.enterpriselab.api.dashboard.domain.ClosedStats;
import com.enterpriselab.api.dashboard.domain.DashboardStatistics;
import com.enterpriselab.api.dashboard.domain.OrderCount;
import com.enterpriselab.api.dashboard.domain.OwnerLoad;
import com.enterpriselab.api.dashboard.domain.StatisticsSnapshot;
import com.enterpriselab.api.workorders.domain.Priority;
import com.enterpriselab.api.workorders.domain.WorkOrderStatus;
import com.enterpriselab.api.workorders.domain.WorkOrderType;

/**
 * Lecturas agregadas sobre {@code work_orders} con SQL de solo lectura (design.md §1): no
 * usa las entities de {@code workorders}. Las dos consultas de {@link #snapshot} corren
 * en una transacción {@code REPEATABLE READ}, así los conteos y las cerradas salen de la
 * misma foto de la base.
 */
@Repository
class DashboardStatisticsAdapter implements DashboardStatistics {

    private static final String COUNTS_SQL = "select status, priority, type, count(*) from work_orders "
            + "group by status, priority, type";

    /** El promedio sale en minutos y sin redondear; el dominio lo redondea. Solo se lee de la fila completed. */
    private static final String CLOSED_SQL = "select status, count(*), "
            + "avg(extract(epoch from closed_at - created_at)) / 60 from work_orders "
            + "where status in ('completed', 'cancelled') and closed_at >= ? and closed_at < ? group by status";

    private static final String OWNERS_SQL = "select taken_by_id, "
            + "(array_agg(taken_by_name order by taken_at desc))[1], count(*) from work_orders "
            + "where status = 'in-progress' group by taken_by_id";

    private final JdbcTemplate jdbc;

    DashboardStatisticsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StatisticsSnapshot snapshot(Instant fromInclusive, Instant toExclusive) {
        List<OrderCount> counts = jdbc.query(COUNTS_SQL, (rs, row) -> new OrderCount(
                WorkOrderStatus.fromValue(rs.getString(1)), Priority.fromValue(rs.getString(2)),
                WorkOrderType.fromValue(rs.getString(3)), rs.getLong(4)));

        long[] closed = new long[2];
        BigDecimal[] average = new BigDecimal[1];
        jdbc.query(CLOSED_SQL, rs -> {
            if (rs.getString(1).equals(WorkOrderStatus.COMPLETED.toValue())) {
                closed[0] = rs.getLong(2);
                average[0] = rs.getBigDecimal(3);
            } else {
                closed[1] = rs.getLong(2);
            }
        }, utc(fromInclusive), utc(toExclusive));

        return new StatisticsSnapshot(new ArrayList<>(counts), new ClosedStats(closed[0], closed[1], average[0]));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OwnerLoad> inProgressByOwner() {
        return jdbc.query(OWNERS_SQL, (rs, row) -> new OwnerLoad(rs.getLong(1), rs.getString(2), rs.getLong(3)));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
