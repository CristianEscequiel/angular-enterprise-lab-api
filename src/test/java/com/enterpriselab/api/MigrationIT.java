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
        assertThat(technicianColumns).containsExactlyInAnyOrder(
                "id", "legajo", "first_name", "last_name", "specialty", "team_type");

        List<String> userColumns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'users'",
                String.class);
        assertThat(userColumns).containsExactlyInAnyOrder(
                "id", "username", "password_hash", "role", "technician_id", "display_name", "email");
    }

    /**
     * Los inserts de estos tests llevan {@code display_name} y {@code email}
     * válidos a propósito: si faltaran, fallarían por el {@code NOT NULL} de la
     * enmienda 00-A y no por el {@code CHECK} que cada test quiere probar. Cada
     * uno verifica además el nombre de la constraint violada.
     */
    @Test
    void tecnicoWithoutTechnicianIdViolatesCheckConstraint() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update(
                        "insert into users (username, password_hash, display_name, email, role, technician_id) "
                                + "values (?, ?, 'Nombre', 'mail@test.dev', 'tecnico', null)",
                        "tecnico.sin.legajo", "hash"))
                .withMessageContaining("users_tecnico_has_legajo");
    }

    @Test
    void nonTecnicoWithTechnicianIdViolatesCheckConstraint() {
        Long technicianId = jdbcTemplate.queryForObject(
                "insert into technicians (legajo, first_name, last_name, specialty, team_type) "
                        + "values (?, 'Test', 'Migration', 'general', 'guardia') returning id",
                Long.class, "99000001");

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update(
                        "insert into users (username, password_hash, display_name, email, role, technician_id) "
                                + "values (?, ?, 'Nombre', 'mail@test.dev', 'administrador', ?)",
                        "administrador.con.legajo", "hash", technicianId))
                .withMessageContaining("users_tecnico_has_legajo");
    }

    @Test
    void roleOutsideTheFourValidValuesViolatesCheckConstraint() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update(
                        "insert into users (username, password_hash, display_name, email, role, technician_id) "
                                + "values (?, ?, 'Nombre', 'mail@test.dev', 'rol-inventado', null)",
                        "usuario.rol.invalido", "hash"))
                .withMessageContaining("users_role_check");
    }

    /** REQ-15: nombre visible y correo son obligatorios. */
    @Test
    void displayNameAndEmailAreRequiredColumns() {
        List<String> nullable = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'users' and is_nullable = 'YES' "
                        + "and column_name in ('display_name', 'email')",
                String.class);

        assertThat(nullable).isEmpty();
    }

    @Test
    void aMissingDisplayNameOrEmailViolatesTheNotNullConstraint() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update(
                        "insert into users (username, password_hash, display_name, email, role) "
                                + "values (?, ?, null, 'mail@test.dev', 'administrador')",
                        "sin.nombre", "hash"))
                .withMessageContaining("display_name");
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update(
                        "insert into users (username, password_hash, display_name, email, role) "
                                + "values (?, ?, 'Nombre', null, 'administrador')",
                        "sin.correo", "hash"))
                .withMessageContaining("email");
    }

    @Test
    void aBlankDisplayNameOrEmailViolatesTheCheckConstraint() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update(
                        "insert into users (username, password_hash, display_name, email, role) "
                                + "values (?, ?, '   ', 'mail@test.dev', 'administrador')",
                        "nombre.en.blanco", "hash"))
                .withMessageContaining("users_profile_not_blank");
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update(
                        "insert into users (username, password_hash, display_name, email, role) "
                                + "values (?, ?, 'Nombre', '', 'administrador')",
                        "correo.vacio", "hash"))
                .withMessageContaining("users_profile_not_blank");
    }
}
