package com.enterpriselab.api;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-37: con el perfil "dev" (única condición bajo la que
 * {@code V2_1__seed_maintenance.sql} entra en {@code spring.flyway.locations})
 * quedan cargados los técnicos y equipos de {@code db.json}. Que no se cargue
 * en otros perfiles lo verifica {@code MaintenanceUpgradeIT}, con una base
 * propia (este contenedor se comparte entre perfiles).
 */
@ActiveProfiles("dev")
class SeedMaintenanceIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedLoadsTheThreeTechnicians() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select legajo, first_name, last_name, specialty, team_type from technicians "
                        + "where legajo in ('1001', '1002', '1003') order by legajo");

        assertThat(rows).extracting(r -> r.get("legajo") + "|" + r.get("first_name") + "|" + r.get("last_name")
                + "|" + r.get("specialty") + "|" + r.get("team_type")).containsExactly(
                "1001|Ana|Ruiz|mecanico|guardia",
                "1002|Luis|Paz|electricista|preventivo-correctivo",
                "1003|Marta|Gómez|general|preventivo-correctivo");
    }

    @Test
    void seedLoadsTheTwoTeamsWithTheirMembers() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select t.name, t.type, tc.legajo from teams t "
                        + "join team_members m on m.team_id = t.id "
                        + "join technicians tc on tc.id = m.technician_id "
                        + "where t.name in ('Guardia mecánica', 'Preventivo eléctrico') order by t.name");

        assertThat(rows).extracting(r -> r.get("name") + "|" + r.get("type") + "|" + r.get("legajo"))
                .containsExactly(
                        "Guardia mecánica|guardia|1001",
                        "Preventivo eléctrico|preventivo-correctivo|1002");
    }

    @Test
    void seededLoginsKeepPointingAtTheirTechnicians() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select u.username, t.legajo from users u join technicians t on t.id = u.technician_id "
                        + "order by u.username");

        assertThat(rows).extracting(r -> r.get("username") + "|" + r.get("legajo"))
                .containsExactly("electricista|1002", "tecnico|1001");
    }

    @Test
    void technician1003HasNoLogin() {
        Integer logins = jdbcTemplate.queryForObject(
                "select count(*) from users u join technicians t on t.id = u.technician_id "
                        + "where t.legajo = '1003'", Integer.class);

        assertThat(logins).isZero();
    }
}
