package com.enterpriselab.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-36 y REQ-37: prueba las migraciones de la spec 01 sobre una base propia,
 * fuera del contexto de Spring. Es la única prueba que reproduce el caso real
 * de un dev que ya tenía {@code V1} y {@code V1_1} aplicadas (design.md §2.2),
 * y la única que verifica que sin el seed no se carga ningún dato: las IT
 * comparten un contenedor donde el seed siempre está incluido (ver
 * {@link AbstractPostgresIT}).
 *
 * <p>Usa el contenedor de {@link AbstractPostgresIT} (mismo paquete) pero crea
 * una base vacía por test, así no depende de lo que dejaron las otras IT.
 */
class MaintenanceUpgradeIT {

    private static final String[] SCHEMA_AND_SEED = {"classpath:db/migration", "classpath:db/seed"};
    private static final String[] SCHEMA_ONLY = {"classpath:db/migration"};

    private String databaseName;
    private JdbcTemplate admin;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createEmptyDatabase() {
        databaseName = "upgrade_it_" + UUID.randomUUID().toString().replace("-", "");
        admin = new JdbcTemplate(new DriverManagerDataSource(AbstractPostgresIT.POSTGRES.getJdbcUrl(),
                AbstractPostgresIT.POSTGRES.getUsername(), AbstractPostgresIT.POSTGRES.getPassword()));
        admin.execute("create database " + databaseName);

        String url = "jdbc:postgresql://" + AbstractPostgresIT.POSTGRES.getHost() + ":"
                + AbstractPostgresIT.POSTGRES.getMappedPort(5432) + "/" + databaseName;
        dataSource = new DriverManagerDataSource(url, AbstractPostgresIT.POSTGRES.getUsername(),
                AbstractPostgresIT.POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void dropDatabase() {
        admin.execute("drop database if exists " + databaseName + " with (force)");
    }

    @Test
    void upgradingADevDatabaseKeepsSeededTechniciansAndTheirLogins() {
        // Estado de un dev que ya tenía la spec 00: V1 y el seed V1_1.
        flyway(SCHEMA_AND_SEED, "1.1").migrate();
        List<Map<String, Object>> techniciansBefore = jdbc.queryForList(
                "select id, legajo from technicians order by legajo");
        List<Map<String, Object>> loginsBefore = jdbc.queryForList(
                "select username, technician_id from users where technician_id is not null order by username");
        assertThat(techniciansBefore).extracting(row -> row.get("legajo")).containsExactly("1001", "1002");

        // Se aplican V2, V2_1 y V3 encima.
        flyway(SCHEMA_AND_SEED, null).migrate();

        List<Map<String, Object>> techniciansAfter = jdbc.queryForList(
                "select id, legajo, first_name, last_name, specialty, team_type from technicians order by legajo");
        assertThat(techniciansAfter).hasSize(3);
        // 1001 y 1002 conservan su id: se completaron con UPDATE, no se reinsertaron.
        assertThat(techniciansAfter.subList(0, 2)).extracting(row -> row.get("id") + "|" + row.get("legajo"))
                .containsExactlyElementsOf(techniciansBefore.stream()
                        .map(row -> row.get("id") + "|" + row.get("legajo")).toList());
        assertThat(techniciansAfter).extracting(row -> row.get("legajo") + "|" + row.get("first_name") + "|"
                + row.get("last_name") + "|" + row.get("specialty") + "|" + row.get("team_type"))
                .containsExactly(
                        "1001|Ana|Ruiz|mecanico|guardia",
                        "1002|Luis|Paz|electricista|preventivo-correctivo",
                        "1003|Marta|Gómez|general|preventivo-correctivo");

        // Los usuarios "tecnico" y "electricista" siguen apuntando a las mismas filas.
        assertThat(jdbc.queryForList(
                "select username, technician_id from users where technician_id is not null order by username"))
                .isEqualTo(loginsBefore);
        assertThat(jdbc.queryForObject(
                "select count(*) from technicians where legajo in ('1001', '1002', '1003')", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from teams", Integer.class)).isEqualTo(2);
    }

    @Test
    void withoutTheSeedNoTechniciansOrTeamsAreLoaded() {
        flyway(SCHEMA_ONLY, null).migrate();

        assertThat(jdbc.queryForObject("select count(*) from technicians", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from teams", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from team_members", Integer.class)).isZero();
    }

    private Flyway flyway(String[] locations, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).locations(locations);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }
}
