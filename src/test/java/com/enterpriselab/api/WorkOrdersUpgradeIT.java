package com.enterpriselab.api;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-44: prueba {@code V7} y {@code V7_1} sobre una base propia, fuera del
 * contexto de Spring (mismo enfoque que {@code MachinesUpgradeIT}). Es la única
 * prueba que verifica que la próxima orden sea la {@code 33} (el contenedor
 * compartido ya tiene altas de otras IT) y que sin el seed no se carga ninguna
 * orden.
 */
class WorkOrdersUpgradeIT {

    private static final String[] SCHEMA_AND_SEED = {"classpath:db/migration", "classpath:db/seed"};
    private static final String[] SCHEMA_ONLY = {"classpath:db/migration"};

    private String databaseName;
    private JdbcTemplate admin;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createEmptyDatabase() {
        databaseName = "orders_it_" + UUID.randomUUID().toString().replace("-", "");
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
    void withTheSeedThereAre32OrdersAndTheNextOneIs33() {
        flyway(SCHEMA_AND_SEED).migrate();

        assertThat(jdbc.queryForObject("select count(*) from work_orders", Integer.class)).isEqualTo(32);
        assertThat(jdbc.queryForObject("select max(id) from work_orders", Long.class)).isEqualTo(32L);
        Long next = jdbc.queryForObject(
                "insert into work_orders (title, description, machine_id, breadcrumb, type, priority, status, "
                        + "created_at) values ('Nueva', 'Descripción de prueba', 1, 'Envasadora', 'correctivo', "
                        + "'low', 'pending', now()) returning id", Long.class);

        assertThat(next).isEqualTo(33L);
    }

    @Test
    void withoutTheSeedNoOrdersAreLoaded() {
        flyway(SCHEMA_ONLY).migrate();

        assertThat(jdbc.queryForObject("select count(*) from work_orders", Integer.class)).isZero();
    }

    private Flyway flyway(String[] locations) {
        return Flyway.configure().dataSource(dataSource).locations(locations).load();
    }
}
