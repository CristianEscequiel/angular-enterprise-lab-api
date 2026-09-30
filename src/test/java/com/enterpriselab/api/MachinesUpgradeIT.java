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
 * REQ-34: prueba {@code V6} y {@code V6_1} sobre una base propia, fuera del
 * contexto de Spring (mismo enfoque que {@code MaintenanceUpgradeIT}). Es la
 * única prueba que verifica que los próximos ids sean {@code 4} y {@code 11}
 * (el contenedor compartido ya tiene altas de otras IT) y que sin el seed no se
 * carga ninguna máquina ni parte.
 */
class MachinesUpgradeIT {

    private static final String[] SCHEMA_AND_SEED = {"classpath:db/migration", "classpath:db/seed"};
    private static final String[] SCHEMA_ONLY = {"classpath:db/migration"};

    private String databaseName;
    private JdbcTemplate admin;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createEmptyDatabase() {
        databaseName = "machines_it_" + UUID.randomUUID().toString().replace("-", "");
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
    void withTheSeedTheNextMachineIs4AndTheNextPartIs11() {
        flyway(SCHEMA_AND_SEED).migrate();

        assertThat(jdbc.queryForObject("select count(*) from machines", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from parts", Integer.class)).isEqualTo(10);
        Long machineId = jdbc.queryForObject(
                "insert into machines (code, name) values ('NEW-04', 'Nueva') returning id", Long.class);
        Long partId = jdbc.queryForObject(
                "insert into parts (machine_id, parent_id, name) values (?, null, 'Nueva') returning id",
                Long.class, machineId);

        assertThat(machineId).isEqualTo(4L);
        assertThat(partId).isEqualTo(11L);
    }

    @Test
    void withoutTheSeedNoMachinesOrPartsAreLoaded() {
        flyway(SCHEMA_ONLY).migrate();

        assertThat(jdbc.queryForObject("select count(*) from machines", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from parts", Integer.class)).isZero();
    }

    private Flyway flyway(String[] locations) {
        return Flyway.configure().dataSource(dataSource).locations(locations).load();
    }
}
