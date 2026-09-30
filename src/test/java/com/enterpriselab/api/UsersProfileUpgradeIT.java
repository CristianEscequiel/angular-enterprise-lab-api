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
 * REQ-15 y REQ-16 (enmienda 00-A): prueba {@code V4}, {@code V4_1} y {@code V5}
 * sobre una base propia, fuera del contexto de Spring. Es la única prueba que
 * reproduce el caso real de un dev que ya tenía las migraciones hasta la spec 01
 * (design.md §10.2), y la única que verifica que sin el seed no se carga ningún
 * usuario: las IT comparten un contenedor donde el seed siempre está incluido
 * (ver {@link AbstractPostgresIT}).
 *
 * <p>Mismo patrón que {@link MaintenanceUpgradeIT}: usa el contenedor de
 * {@link AbstractPostgresIT} y crea una base vacía por test.
 */
class UsersProfileUpgradeIT {

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
    void upgradingADatabaseAlreadyMigratedThroughSpec01KeepsEveryUserAndCompletesTheirProfile() {
        // Estado de un dev que ya tenía la spec 00 y la spec 01: hasta V3, con los cinco usuarios sembrados.
        flyway(SCHEMA_AND_SEED, "3").migrate();
        List<Map<String, Object>> usersBefore = jdbc.queryForList(
                "select id, username, password_hash, role, technician_id from users order by username");
        assertThat(usersBefore).extracting(row -> row.get("username"))
                .containsExactly("admin", "electricista", "produccion", "teamleader", "tecnico");
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where table_name = 'users' and column_name in ('display_name', 'email')",
                Integer.class)).isZero();

        // Se aplican V4, V4_1 y V5 encima.
        flyway(SCHEMA_AND_SEED, null).migrate();

        // Los usuarios se completaron con UPDATE: conservan id, contraseña, rol y vínculo con el técnico.
        assertThat(jdbc.queryForList(
                "select id, username, password_hash, role, technician_id from users order by username"))
                .isEqualTo(usersBefore);
        assertThat(jdbc.queryForList("select username, display_name, email from users order by username"))
                .extracting(row -> row.get("username") + "|" + row.get("display_name") + "|" + row.get("email"))
                .containsExactly(
                        "admin|Administrador|admin@enterprise-lab.dev",
                        "electricista|Técnico Electricista Preventivo|electricista@enterprise-lab.dev",
                        "produccion|Personal de Producción|produccion@enterprise-lab.dev",
                        "teamleader|Team Leader de Mantenimiento|teamleader@enterprise-lab.dev",
                        "tecnico|Técnico Mecánico de Guardia|tecnico@enterprise-lab.dev");
        // Y las columnas quedaron obligatorias.
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_name = 'users' "
                        + "and column_name in ('display_name', 'email') and is_nullable = 'NO'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void withoutTheSeedNoUsersAreLoaded() {
        flyway(SCHEMA_ONLY, null).migrate();

        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from technicians", Integer.class)).isZero();
    }

    private Flyway flyway(String[] locations, String target) {
        var configuration = Flyway.configure().dataSource(dataSource).locations(locations);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }
}
