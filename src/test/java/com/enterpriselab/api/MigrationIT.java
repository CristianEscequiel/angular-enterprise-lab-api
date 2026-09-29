package com.enterpriselab.api;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * REQ-4, REQ-5: {@code V1__init.sql} deja creadas {@code technicians} y
 * {@code users} con las columnas esperadas, y hace valer a nivel de base de
 * datos que (a) {@code role} sea uno de los cuatro valores válidos y (b) un
 * usuario {@code tecnico} siempre tenga {@code technician_id} (y viceversa).
 */
class MigrationIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void technicansAndUsersTablesExistWithExpectedColumns() {
        List<String> technicianColumns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'technicians'",
                String.class);
        assertThat(technicianColumns).containsExactlyInAnyOrder("id", "legajo");

        List<String> userColumns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'users'",
                String.class);
        assertThat(userColumns).containsExactlyInAnyOrder(
                "id", "username", "password_hash", "role", "technician_id");
    }

    @Test
    void tecnicoWithoutTechnicianIdViolatesCheckConstraint() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update(
                        "insert into users (username, password_hash, role, technician_id) "
                                + "values (?, ?, 'tecnico', null)",
                        "tecnico.sin.legajo", "hash"));
    }

    @Test
    void nonTecnicoWithTechnicianIdViolatesCheckConstraint() {
        Long technicianId = jdbcTemplate.queryForObject(
                "insert into technicians (legajo) values (?) returning id",
                Long.class, "LEG-MIGRATION-IT");

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update(
                        "insert into users (username, password_hash, role, technician_id) "
                                + "values (?, ?, 'administrador', ?)",
                        "administrador.con.legajo", "hash", technicianId));
    }

    @Test
    void roleOutsideTheFourValidValuesViolatesCheckConstraint() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update(
                        "insert into users (username, password_hash, role, technician_id) "
                                + "values (?, ?, 'rol-inventado', null)",
                        "usuario.rol.invalido", "hash"));
    }
}
