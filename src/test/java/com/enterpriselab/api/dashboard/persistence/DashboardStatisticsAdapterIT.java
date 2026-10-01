package com.enterpriselab.api.dashboard.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.enterpriselab.api.AbstractPostgresIT;
import com.enterpriselab.api.dashboard.domain.OrderCount;
import com.enterpriselab.api.dashboard.domain.OwnerLoad;
import com.enterpriselab.api.dashboard.domain.StatisticsSnapshot;
import com.enterpriselab.api.workorders.domain.Priority;
import com.enterpriselab.api.workorders.domain.WorkOrderStatus;
import com.enterpriselab.api.workorders.domain.WorkOrderType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-2 a REQ-7, REQ-11 a REQ-13, REQ-15 y REQ-17 con cifras exactas: cada test migra una
 * <strong>base propia</strong> (el contenedor compartido ya tiene las órdenes de otras IT) e
 * inserta por SQL las órdenes con fechas controladas. Mismo enfoque que
 * {@code WorkOrdersUpgradeIT}; el adaptador se arma a mano sobre esa base.
 */
class DashboardStatisticsAdapterIT {

    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO_EXCLUSIVE = Instant.parse("2026-09-30T00:00:00Z");

    private String databaseName;
    private JdbcTemplate admin;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private DashboardStatisticsAdapter adapter;
    private long ana;
    private long beto;

    @BeforeEach
    void createDatabase() {
        databaseName = "dashboard_it_" + UUID.randomUUID().toString().replace("-", "");
        admin = new JdbcTemplate(new DriverManagerDataSource(AbstractPostgresIT.POSTGRES.getJdbcUrl(),
                AbstractPostgresIT.POSTGRES.getUsername(), AbstractPostgresIT.POSTGRES.getPassword()));
        admin.execute("create database " + databaseName);
        dataSource = new DriverManagerDataSource("jdbc:postgresql://" + AbstractPostgresIT.POSTGRES.getHost() + ":"
                + AbstractPostgresIT.POSTGRES.getMappedPort(5432) + "/" + databaseName,
                AbstractPostgresIT.POSTGRES.getUsername(), AbstractPostgresIT.POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        adapter = new DashboardStatisticsAdapter(jdbc);
    }

    @AfterEach
    void dropDatabase() {
        admin.execute("drop database if exists " + databaseName + " with (force)");
    }

    // --- Base vacía (REQ-13, REQ-12, REQ-17) -----------------------------------------------------------

    @Test
    void anEmptyDatabaseHasNoRowsZeroClosedAndNullAverage() {
        migrate(false);

        StatisticsSnapshot snapshot = adapter.snapshot(FROM, TO_EXCLUSIVE);

        assertThat(snapshot.counts()).isEmpty();
        assertThat(snapshot.closed().completed()).isZero();
        assertThat(snapshot.closed().cancelled()).isZero();
        assertThat(snapshot.closed().averageResolutionMinutes()).isNull();
        assertThat(adapter.inProgressByOwner()).isEmpty();
    }

    // --- Conteos (REQ-2 a REQ-5) -----------------------------------------------------------------------

    @Test
    void countsComeGroupedByStatusPriorityAndType() {
        migrate(false);
        users();
        pending("low", "preventivo");
        pending("low", "preventivo");
        pending("high", "correctivo");
        inProgress("high", "correctivo", ana, "Ana", "2026-09-10T10:00:00Z");
        closed("completed", "medium", "pronto-intervencion", ana, "2026-09-01T08:00:00Z", "2026-09-01T09:00:00Z");
        closed("cancelled", "low", "preventivo", ana, "2026-09-01T08:00:00Z", "2026-09-01T09:00:00Z");

        List<OrderCount> counts = adapter.snapshot(FROM, TO_EXCLUSIVE).counts();

        assertThat(counts).containsExactlyInAnyOrder(
                new OrderCount(WorkOrderStatus.PENDING, Priority.LOW, WorkOrderType.PREVENTIVO, 2),
                new OrderCount(WorkOrderStatus.PENDING, Priority.HIGH, WorkOrderType.CORRECTIVO, 1),
                new OrderCount(WorkOrderStatus.IN_PROGRESS, Priority.HIGH, WorkOrderType.CORRECTIVO, 1),
                new OrderCount(WorkOrderStatus.COMPLETED, Priority.MEDIUM, WorkOrderType.PRONTO_INTERVENCION, 1),
                new OrderCount(WorkOrderStatus.CANCELLED, Priority.LOW, WorkOrderType.PREVENTIVO, 1));
    }

    @Test
    void countsIgnoreThePeriod() {
        migrate(false);
        users();
        closed("completed", "low", "preventivo", ana, "2020-01-01T08:00:00Z", "2020-01-01T09:00:00Z");

        assertThat(adapter.snapshot(FROM, TO_EXCLUSIVE).counts()).hasSize(1);
        assertThat(adapter.snapshot(FROM, TO_EXCLUSIVE).closed().completed()).isZero();
    }

    // --- Cerradas en el período (REQ-7) ----------------------------------------------------------------

    @Test
    void thePeriodIncludesTheWholeFirstAndLastDayAndNothingAround() {
        migrate(false);
        users();
        closed("completed", "low", "preventivo", ana, "2026-08-31T20:00:00Z", "2026-08-31T23:59:59.999Z");
        closed("completed", "low", "preventivo", ana, "2026-08-31T20:00:00Z", "2026-09-01T00:00:00Z");
        closed("completed", "low", "preventivo", ana, "2026-09-10T08:00:00Z", "2026-09-10T12:00:00Z");
        closed("completed", "low", "preventivo", ana, "2026-09-29T08:00:00Z", "2026-09-29T23:59:59.999Z");
        closed("completed", "low", "preventivo", ana, "2026-09-29T08:00:00Z", "2026-09-30T00:00:00Z");

        assertThat(adapter.snapshot(FROM, TO_EXCLUSIVE).closed().completed()).isEqualTo(3);
    }

    @Test
    void completedAndCancelledAreCountedSeparately() {
        migrate(false);
        users();
        closed("completed", "low", "preventivo", ana, "2026-09-02T08:00:00Z", "2026-09-02T09:00:00Z");
        closed("completed", "low", "preventivo", beto, "2026-09-03T08:00:00Z", "2026-09-03T09:00:00Z");
        closed("cancelled", "low", "preventivo", ana, "2026-09-04T08:00:00Z", "2026-09-04T09:00:00Z");
        pending("low", "preventivo");
        inProgress("low", "preventivo", ana, "Ana", "2026-09-05T08:00:00Z");

        var closed = adapter.snapshot(FROM, TO_EXCLUSIVE).closed();

        assertThat(closed.completed()).isEqualTo(2);
        assertThat(closed.cancelled()).isEqualTo(1);
    }

    // --- Promedio (REQ-11, REQ-12) ---------------------------------------------------------------------

    @Test
    void theAverageIsInMinutesOfTheCompletedOrdersClosedInThePeriodOnly() {
        migrate(false);
        users();
        // 60 min y 90 min dentro del período: promedio 75
        closed("completed", "low", "preventivo", ana, "2026-09-10T08:00:00Z", "2026-09-10T09:00:00Z");
        closed("completed", "low", "preventivo", ana, "2026-09-11T08:00:00Z", "2026-09-11T09:30:00Z");
        // una cancelada de 10 días y una completed cerrada fuera del período no entran
        closed("cancelled", "low", "preventivo", ana, "2026-09-01T08:00:00Z", "2026-09-11T08:00:00Z");
        closed("completed", "low", "preventivo", ana, "2026-07-01T08:00:00Z", "2026-07-20T08:00:00Z");

        BigDecimal average = adapter.snapshot(FROM, TO_EXCLUSIVE).closed().averageResolutionMinutes();

        assertThat(average).isEqualByComparingTo("75");
    }

    @Test
    void thereIsNoAverageWhenOnlyCancelledOrdersWereClosedInThePeriod() {
        migrate(false);
        users();
        closed("cancelled", "low", "preventivo", ana, "2026-09-10T08:00:00Z", "2026-09-10T09:00:00Z");

        var closed = adapter.snapshot(FROM, TO_EXCLUSIVE).closed();

        assertThat(closed.cancelled()).isEqualTo(1);
        assertThat(closed.averageResolutionMinutes()).isNull();
    }

    @Test
    void theSeededOrdersGiveANonNegativeAverageAndTheKnownCounts() {
        migrate(true);

        StatisticsSnapshot snapshot = adapter.snapshot(Instant.parse("2000-01-01T00:00:00Z"),
                Instant.parse("2100-01-01T00:00:00Z"));

        assertThat(snapshot.counts().stream().mapToLong(OrderCount::count).sum()).isEqualTo(32);
        assertThat(snapshot.closed().completed()).isEqualTo(9);
        assertThat(snapshot.closed().cancelled()).isEqualTo(2);
        assertThat(snapshot.closed().averageResolutionMinutes()).isNotNull().isNotNegative();
    }

    // --- Carga por técnico (REQ-15, REQ-17) ------------------------------------------------------------

    @Test
    void workloadCountsOnlyInProgressOrdersPerOwner() {
        migrate(false);
        users();
        inProgress("low", "preventivo", ana, "Ana", "2026-09-10T10:00:00Z");
        inProgress("low", "preventivo", ana, "Ana", "2026-09-11T10:00:00Z");
        inProgress("low", "preventivo", beto, "Beto", "2026-09-12T10:00:00Z");
        closed("completed", "low", "preventivo", beto, "2026-09-01T08:00:00Z", "2026-09-01T09:00:00Z");
        pending("low", "preventivo");

        assertThat(adapter.inProgressByOwner()).containsExactlyInAnyOrder(
                new OwnerLoad(ana, "Ana", 2), new OwnerLoad(beto, "Beto", 1));
    }

    @Test
    void workloadUsesTheNameOfTheMostRecentTake() {
        migrate(false);
        users();
        inProgress("low", "preventivo", ana, "Ana vieja", "2026-09-10T10:00:00Z");
        inProgress("low", "preventivo", ana, "Ana nueva", "2026-09-12T10:00:00Z");
        inProgress("low", "preventivo", ana, "Ana intermedia", "2026-09-11T10:00:00Z");

        assertThat(adapter.inProgressByOwner()).containsExactly(new OwnerLoad(ana, "Ana nueva", 3));
    }

    // --- Ayudas ----------------------------------------------------------------------------------------

    private void migrate(boolean withSeed) {
        String[] locations = withSeed ? new String[] {"classpath:db/migration", "classpath:db/seed"}
                : new String[] {"classpath:db/migration"};
        Flyway.configure().dataSource(dataSource).locations(locations).load().migrate();
    }

    private void users() {
        ana = user("ana");
        beto = user("beto");
    }

    private long user(String username) {
        return jdbc.queryForObject("insert into users (username, password_hash, display_name, email, role) "
                + "values (?, 'hash', ?, ?, 'administrador') returning id", Long.class, username, username,
                username + "@enterprise-lab.dev");
    }

    private void pending(String priority, String type) {
        jdbc.update("insert into work_orders (title, description, machine_id, breadcrumb, type, priority, status, "
                + "created_at) values ('t', 'Descripción de prueba', 1, 'M', ?, ?, 'pending', now())", type, priority);
    }

    private void inProgress(String priority, String type, long ownerId, String ownerName, String takenAt) {
        jdbc.update("insert into work_orders (title, description, machine_id, breadcrumb, type, priority, status, "
                + "created_at, taken_by_id, taken_by_name, taken_at) values ('t', 'Descripción de prueba', 1, 'M', "
                + "?, ?, 'in-progress', now(), ?, ?, ?)", type, priority, ownerId, ownerName, at(takenAt));
    }

    private void closed(String status, String priority, String type, long ownerId, String createdAt, String closedAt) {
        jdbc.update("insert into work_orders (title, description, machine_id, breadcrumb, type, priority, status, "
                + "created_at, taken_by_id, taken_by_name, taken_at, closing_comment, closing_author_id, "
                + "closing_author_name, closed_at) values ('t', 'Descripción de prueba', 1, 'M', ?, ?, ?, ?, ?, "
                + "'Dueño', ?, 'Nota de cierre', ?, 'Dueño', ?)", type, priority, status, at(createdAt), ownerId,
                at(createdAt), ownerId, at(closedAt));
    }

    private static OffsetDateTime at(String instant) {
        return OffsetDateTime.ofInstant(Instant.parse(instant), ZoneOffset.UTC);
    }
}
