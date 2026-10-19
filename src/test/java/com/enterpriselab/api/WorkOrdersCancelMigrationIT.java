package com.enterpriselab.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Enmienda 04-A, REQ-41 y REQ-47: {@code V9} acepta una {@code cancelled} sin dueño ni comentario
 * (con autor y {@code closed_at}) y sigue rechazando por {@code CHECK} las demás combinaciones
 * inválidas. Las filas se insertan por SQL directo con títulos propios ({@code WOCANIT-*}) y se
 * limpian al terminar. {@link #req41_v9KeepsThe32SeededOrdersUntouched} migra una base propia
 * hasta {@code V7_1} y de ahí a la última versión.
 */
class WorkOrdersCancelMigrationIT extends AbstractPostgresIT {

    private static final String COMMENT = "Nota de cierre";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long technicianId;
    private long otherId;

    @BeforeEach
    void findUsers() {
        technicianId = jdbcTemplate.queryForObject("select id from users where username = 'tecnico'", Long.class);
        otherId = jdbcTemplate.queryForObject("select id from users where username = 'electricista'", Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from work_orders where title like 'WOCANIT-%'");
    }

    // --- Aceptada (REQ-41) -----------------------------------------------------------------------------

    @Test
    void req41_aCancelledOrderWithoutOwnerNorCommentButWithAuthorAndClosedAtIsAccepted() {
        insert("cancelled", null, null, technicianId, "Autor", true);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from work_orders where title like 'WOCANIT-%' and status = 'cancelled' "
                        + "and taken_by_id is null and closing_comment is null", Integer.class)).isEqualTo(1);
    }

    @Test
    void req41_theCombinationsOfV8StayAccepted() {
        insert("pending", null, null, null, null, false);
        insert("in-progress", technicianId, null, null, null, false);
        insert("completed", technicianId, COMMENT, technicianId, "Dueño", true);
        insert("cancelled", technicianId, COMMENT, technicianId, "Dueño", true);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from work_orders where title like 'WOCANIT-%'", Integer.class)).isEqualTo(4);
    }

    // --- Rechazadas (REQ-47) ---------------------------------------------------------------------------

    @Test
    void req47_pendingOrInProgressWithAClosingNoteIsRejected() {
        assertRejected("pending", null, null, technicianId, "Autor", true);
        assertRejected("pending", null, COMMENT, technicianId, "Autor", true);
        assertRejected("in-progress", technicianId, null, technicianId, "Autor", true);
        assertRejected("in-progress", technicianId, COMMENT, technicianId, "Dueño", true);
    }

    @Test
    void req47_completedWithoutOwnerWithoutCommentOrWithAnAuthorOtherThanTheOwnerIsRejected() {
        assertRejected("completed", null, null, technicianId, "Autor", true);
        assertRejected("completed", null, COMMENT, technicianId, "Autor", true);
        assertRejected("completed", technicianId, null, technicianId, "Dueño", true);
        assertRejected("completed", technicianId, COMMENT, otherId, "Otro", true);
    }

    @Test
    void req47_cancelledWithOwnerButWithoutCommentOrWithAnAuthorOtherThanTheOwnerIsRejected() {
        assertRejected("cancelled", technicianId, null, technicianId, "Dueño", true);
        assertRejected("cancelled", technicianId, COMMENT, otherId, "Otro", true);
        assertRejected("cancelled", technicianId, null, otherId, "Otro", true);
    }

    @Test
    void req47_cancelledWithoutOwnerAndWithACommentIsRejected() {
        assertRejected("cancelled", null, COMMENT, technicianId, "Autor", true);
    }

    @Test
    void req47_cancelledWithoutOwnerAndWithoutAuthorNameOrClosedAtIsRejected() {
        assertRejected("cancelled", null, null, null, null, false);
        assertRejected("cancelled", null, null, technicianId, null, true);
        assertRejected("cancelled", null, null, null, "Autor", true);
        assertRejected("cancelled", null, null, technicianId, "Autor", false);
    }

    // --- Base propia: V7 + V7_1 -> última --------------------------------------------------------------

    @Test
    void req41_v9KeepsThe32SeededOrdersUntouched() {
        String databaseName = "cancel_it_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()));
        admin.execute("create database " + databaseName);
        try {
            DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:postgresql://"
                    + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + databaseName,
                    POSTGRES.getUsername(), POSTGRES.getPassword());
            String[] locations = {"classpath:db/migration", "classpath:db/seed"};
            Flyway.configure().dataSource(dataSource).locations(locations).target("7.1").load().migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            String snapshot = "select * from work_orders order by id";
            List<Map<String, Object>> before = jdbc.queryForList(snapshot);
            assertThat(before).hasSize(32);

            Flyway.configure().dataSource(dataSource).locations(locations).target("9").load().migrate();

            assertThat(jdbc.queryForList(snapshot)).isEqualTo(before);
            assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname in "
                    + "('work_orders_owner_group_check', 'work_orders_closing_group_check', "
                    + "'work_orders_state_invariants_check')", Integer.class)).isEqualTo(3);
        } finally {
            admin.execute("drop database if exists " + databaseName + " with (force)");
        }
    }

    // --- Ayudas ----------------------------------------------------------------------------------------

    private void assertRejected(String status, Long ownerId, String comment, Long authorId, String authorName,
            boolean withClosedAt) {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .as("%s owner=%s comment=%s author=%s/%s closedAt=%s", status, ownerId, comment, authorId,
                        authorName, withClosedAt)
                .isThrownBy(() -> insert(status, ownerId, comment, authorId, authorName, withClosedAt))
                .withMessageContaining("check constraint");
    }

    private void insert(String status, Long ownerId, String comment, Long authorId, String authorName,
            boolean withClosedAt) {
        jdbcTemplate.update("insert into work_orders (title, description, machine_id, breadcrumb, type, priority, "
                + "status, created_at, taken_by_id, taken_by_name, taken_at, closing_comment, closing_author_id, "
                + "closing_author_name, closed_at) values (?, 'Descripción de prueba', 1, 'M', 'preventivo', 'low', "
                + "?, now(), ?, ?, " + (ownerId == null ? "null" : "now()") + ", ?, ?, ?, "
                + (withClosedAt ? "now()" : "null") + ")",
                "WOCANIT-" + UUID.randomUUID(), status, ownerId, ownerId == null ? null : "Dueño", comment, authorId,
                authorName);
    }
}
