package com.enterpriselab.api;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-44: con el perfil "dev" (única condición bajo la que
 * {@code V7_1__seed_work_orders.sql} entra en {@code spring.flyway.locations})
 * quedan cargadas las 32 órdenes de {@code db.json}. El contenedor se comparte
 * con otras IT que crean órdenes propias, así que este test afirma sobre los ids
 * {@code 1} a {@code 32}; que el próximo id sea {@code 33} y que fuera de
 * {@code dev} no se cargue nada lo verifica {@code WorkOrdersUpgradeIT}, con una
 * base propia.
 */
@ActiveProfiles("dev")
class SeedWorkOrdersIT extends AbstractPostgresIT {

    private static final String SEEDED = "id between 1 and 32";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void theThirtyTwoOrdersAreLoadedWithTheirStatusDistribution() {
        assertThat(count("select count(*) from work_orders where " + SEEDED)).isEqualTo(32);
        Map<String, Integer> byStatus = new java.util.HashMap<>();
        jdbcTemplate.query("select status, count(*) as n from work_orders where " + SEEDED + " group by status",
                rs -> {
                    byStatus.put(rs.getString("status"), rs.getInt("n"));
                });

        assertThat(byStatus).containsOnly(Map.entry("pending", 12), Map.entry("in-progress", 9),
                Map.entry("completed", 9), Map.entry("cancelled", 2));
    }

    @Test
    void theTypeAndPriorityDistributionMatchesDbJson() {
        assertThat(count("select count(*) from work_orders where " + SEEDED + " and type = 'preventivo'"))
                .isEqualTo(20);
        assertThat(count("select count(*) from work_orders where " + SEEDED + " and type = 'correctivo'"))
                .isEqualTo(8);
        assertThat(count("select count(*) from work_orders where " + SEEDED + " and type = 'pronto-intervencion'"))
                .isEqualTo(4);
        assertThat(count("select count(*) from work_orders where " + SEEDED + " and priority = 'high'"))
                .isEqualTo(11);
    }

    @Test
    void theThreeOrdersWithAlphanumericIdsWereRenumberedAs30To32InDbJsonOrder() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id, status from work_orders where id between 30 and 32 order by id");

        assertThat(rows).extracting(r -> r.get("id") + "|" + r.get("status"))
                .containsExactly("30|pending", "31|pending", "32|pending");
    }

    @Test
    void createdAtWithoutTimeZoneInDbJsonIsUtc() {
        List<String> created = jdbcTemplate.queryForList(
                "select to_char(created_at at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS') "
                        + "from work_orders where id in (1, 2, 3) order by id", String.class);

        assertThat(created).containsExactly("2026-08-01T10:30:00", "2026-08-02T08:15:00", "2026-08-03T14:00:00");
    }

    @Test
    void ordersWithAnOwnerPointToTheTecnicoAndElectricistaUsersKeepingTheNamesOfDbJson() {
        List<Map<String, Object>> owners = jdbcTemplate.queryForList(
                "select u.username, w.taken_by_name, count(*) as n from work_orders w "
                        + "join users u on u.id = w.taken_by_id where w.id between 1 and 32 "
                        + "group by u.username, w.taken_by_name order by u.username");

        assertThat(owners).extracting(r -> r.get("username") + "|" + r.get("taken_by_name") + "|" + r.get("n"))
                .containsExactly("electricista|Técnico Electricista Preventivo|17",
                        "tecnico|Técnico Mecánico de Guardia|3");
    }

    @Test
    void stateOwnerAndClosingNoteAreConsistent() {
        assertThat(count("select count(*) from work_orders where " + SEEDED
                + " and (status = 'pending') <> (taken_by_id is null)")).isZero();
        assertThat(count("select count(*) from work_orders where " + SEEDED
                + " and (status in ('completed', 'cancelled')) <> (closing_author_id is not null)")).isZero();
        assertThat(count("select count(*) from work_orders where " + SEEDED
                + " and closing_author_id is not null and closing_author_id <> taken_by_id")).isZero();
        assertThat(count("select count(*) from work_orders where " + SEEDED
                + " and closing_author_id is not null and (closing_author_name <> taken_by_name "
                + "or closing_comment is null or closed_at is null)")).isZero();
    }

    @Test
    void theClosingNoteOfOrder3KeepsItsTextAndInstant() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select closing_comment, to_char(closed_at at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"') "
                        + "as closed, to_char(taken_at at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"') as taken "
                        + "from work_orders where id = 3");

        assertThat(row.get("closing_comment")).isEqualTo(
                "Se reemplazó el rodamiento y se verificó el giro sin vibración ni ruido anormal.");
        assertThat(row.get("closed")).isEqualTo("2026-08-03T20:00:00.000Z");
        assertThat(row.get("taken")).isEqualTo("2026-08-03T15:00:00.000Z");
    }

    @Test
    void everyBreadcrumbIsTheOneTheSeededMachineAndPartTreeBuilds() {
        List<Map<String, Object>> mismatches = jdbcTemplate.queryForList(
                "with recursive path(id, names) as ("
                        + "  select p.id, p.name::text from parts p where p.parent_id is null"
                        + "  union all"
                        + "  select c.id, path.names || ' > ' || c.name from parts c join path on c.parent_id = path.id) "
                        + "select w.id, w.breadcrumb, m.name || coalesce(' > ' || path.names, '') as expected "
                        + "from work_orders w join machines m on m.id = w.machine_id "
                        + "left join path on path.id = w.part_id "
                        + "where w.id between 1 and 32 and w.breadcrumb <> m.name || coalesce(' > ' || path.names, '')");

        assertThat(mismatches).isEmpty();
        assertThat(count("select count(*) from work_orders w join machines m on m.id = w.machine_id where w."
                + SEEDED)).isEqualTo(32);
        assertThat(count("select count(*) from work_orders w join parts p on p.id = w.part_id "
                + "where w.id between 1 and 32 and p.machine_id <> w.machine_id")).isZero();
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
