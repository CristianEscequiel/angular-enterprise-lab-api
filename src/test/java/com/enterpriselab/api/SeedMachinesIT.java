package com.enterpriselab.api;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-34: con el perfil "dev" (única condición bajo la que
 * {@code V6_1__seed_machines.sql} entra en {@code spring.flyway.locations})
 * quedan cargadas las máquinas y las partes de {@code db.json} con sus ids.
 * Como el contenedor se comparte, este test no afirma sobre totales sino sobre
 * las filas de los ids {@code 1} a {@code 10}; que los próximos ids sean 4 y 11
 * y que fuera de {@code dev} no se cargue nada lo verifica
 * {@code MachinesUpgradeIT}, con una base propia.
 */
@ActiveProfiles("dev")
class SeedMachinesIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedLoadsTheThreeMachinesKeepingTheirIds() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id, code, name from machines where id between 1 and 3 order by id");

        assertThat(rows).extracting(r -> r.get("id") + "|" + r.get("code") + "|" + r.get("name"))
                .containsExactly("1|ENV-01|Envasadora línea 1", "2|SEL-02|Selladora", "3|ROT-03|Rotuladora");
    }

    @Test
    void seedLoadsTheTenPartsKeepingTheirIdsAndTheTreeShape() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select id, machine_id, parent_id, name from parts where id between 1 and 10 order by id");

        assertThat(rows).extracting(r -> r.get("id") + "|" + r.get("machine_id") + "|" + r.get("parent_id")
                + "|" + r.get("name")).containsExactly(
                        "1|1|null|Mesa de transporte",
                        "2|1|1|Cinta 1",
                        "3|1|2|Motor de cinta",
                        "4|1|3|Rodamiento delantero",
                        "5|1|1|Cinta 2",
                        "6|1|null|Cabezal de sellado",
                        "7|1|6|Resistencia",
                        "8|2|null|Cabezal térmico",
                        "9|2|8|Resistencia",
                        "10|2|null|Mordaza");
    }

    @Test
    void theRotuladoraHasNoParts() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from parts where machine_id = 3", Integer.class))
                .isZero();
    }
}
