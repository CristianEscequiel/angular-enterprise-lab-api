package com.enterpriselab.api;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * REQ-36: {@code V2} y {@code V3} dejan {@code technicians} completa y crean
 * {@code teams} y {@code team_members} con las garantías del diseño (§2.1):
 * FK sin cascada hacia técnicos, unique por par equipo-técnico y borrado en
 * cascada solo desde el equipo. Sin perfil {@code dev}, pero el contenedor se
 * comparte con el resto de las IT, así que cada test usa legajos propios y
 * limpia lo que creó.
 */
class MaintenanceMigrationIT extends AbstractPostgresIT {

    private static final String LEGAJO = "99000101";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from teams where name = 'MaintenanceMigrationIT'");
        jdbcTemplate.update("delete from technicians where legajo = ?", LEGAJO);
    }

    @Test
    void technicianColumnsAreCompleteAndRequired() {
        List<String> nullable = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'technicians' "
                        + "and is_nullable = 'YES'",
                String.class);

        assertThat(nullable).isEmpty();
    }

    @Test
    void teamsAndTeamMembersTablesExistWithExpectedColumns() {
        assertThat(columns("teams")).containsExactlyInAnyOrder("id", "name", "type");
        assertThat(columns("team_members")).containsExactlyInAnyOrder(
                "id", "team_id", "technician_id", "sort_order");
    }

    @Test
    void technicianWithoutRequiredDataOrWithBadLegajoIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update("insert into technicians (legajo) values (?)", LEGAJO));
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update(
                        "insert into technicians (legajo, first_name, last_name, specialty, team_type) "
                                + "values ('123456789', 'A', 'B', 'general', 'guardia')"));
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update(
                        "insert into technicians (legajo, first_name, last_name, specialty, team_type) "
                                + "values (?, 'A', 'B', 'plomero', 'guardia')", LEGAJO));
    }

    @Test
    void deletingATechnicianWhoIsATeamMemberViolatesTheForeignKey() {
        Long technicianId = insertTechnician();
        Long teamId = insertTeam();
        insertMember(teamId, technicianId, 0);

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update("delete from technicians where id = ?", technicianId));
    }

    @Test
    void sameTechnicianTwiceInTheSameTeamViolatesTheUniqueConstraint() {
        Long technicianId = insertTechnician();
        Long teamId = insertTeam();
        insertMember(teamId, technicianId, 0);

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                insertMember(teamId, technicianId, 1));
    }

    @Test
    void deletingATeamDeletesItsMembershipsAndKeepsTheTechnician() {
        Long technicianId = insertTechnician();
        Long teamId = insertTeam();
        insertMember(teamId, technicianId, 0);

        jdbcTemplate.update("delete from teams where id = ?", teamId);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from team_members where team_id = ?", Integer.class, teamId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from technicians where id = ?", Integer.class, technicianId)).isEqualTo(1);
    }

    private List<String> columns(String table) {
        return jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ?",
                String.class, table);
    }

    private Long insertTechnician() {
        return jdbcTemplate.queryForObject(
                "insert into technicians (legajo, first_name, last_name, specialty, team_type) "
                        + "values (?, 'Test', 'Maintenance', 'general', 'guardia') returning id",
                Long.class, LEGAJO);
    }

    private Long insertTeam() {
        return jdbcTemplate.queryForObject(
                "insert into teams (name, type) values ('MaintenanceMigrationIT', 'guardia') returning id",
                Long.class);
    }

    private void insertMember(Long teamId, Long technicianId, int order) {
        jdbcTemplate.update(
                "insert into team_members (team_id, technician_id, sort_order) values (?, ?, ?)",
                teamId, technicianId, order);
    }
}
