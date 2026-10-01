package com.enterpriselab.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * REQ-30: {@code V8} impone por {@code CHECK} las invariantes de estado de una orden
 * (design.md §2). Las filas se insertan por SQL directo con títulos propios
 * ({@code WOINVIT-*}) y se limpian al terminar. {@link #v8KeepsThe32SeededOrdersUntouched}
 * migra una base propia hasta {@code V7_1} y de ahí a {@code V8}.
 */
class WorkOrdersStateInvariantsMigrationIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from work_orders where title like 'WOINVIT-%'");
    }

    // --- Combinaciones válidas -------------------------------------------------------------------------

    @Test
    void aValidCombinationOfEachStatusIsAccepted() {
        insert("pending", false, false);
        insert("in-progress", true, false);
        insert("completed", true, true);
        insert("cancelled", true, true);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from work_orders where title like 'WOINVIT-%'", Integer.class)).isEqualTo(4);
    }

    // --- Combinaciones inválidas -----------------------------------------------------------------------

    @Test
    void pendingWithAnOwnerIsRejected() {
        assertRejected("pending", true, false);
    }

    @Test
    void pendingWithANoteIsRejected() {
        assertRejected("pending", false, true);
        assertRejected("pending", true, true);
    }

    @Test
    void inProgressWithoutAnOwnerIsRejected() {
        assertRejected("in-progress", false, false);
    }

    @Test
    void inProgressWithANoteIsRejected() {
        assertRejected("in-progress", true, true);
    }

    @Test
    void aClosedOrderWithoutOwnerOrWithoutNoteIsRejected() {
        for (String status : List.of("completed", "cancelled")) {
            assertRejected(status, false, false);
            assertRejected(status, true, false);
        }
    }

    @Test
    void aClosedOrderWhoseAuthorIsNotTheOwnerIsRejected() {
        assertRejectedSql("completed", "taken_by_id, taken_by_name, taken_at, closing_comment, "
                + "closing_author_id, closing_author_name, closed_at",
                "(select id from users where username = 'tecnico'), 'Dueño', now(), 'Nota', "
                        + "(select id from users where username = 'electricista'), 'Otro', now()");
    }

    @Test
    void anIncompleteOwnerOrNoteGroupIsRejected() {
        // dueño con taken_at nulo
        assertRejectedSql("in-progress", "taken_by_id, taken_by_name",
                "(select id from users where username = 'tecnico'), 'Dueño'");
        // dueño sin nombre
        assertRejectedSql("in-progress", "taken_by_id, taken_at",
                "(select id from users where username = 'tecnico'), now()");
        // nota sin comentario
        assertRejectedSql("completed", "taken_by_id, taken_by_name, taken_at, closing_author_id, "
                + "closing_author_name, closed_at",
                "(select id from users where username = 'tecnico'), 'Dueño', now(), "
                        + "(select id from users where username = 'tecnico'), 'Dueño', now()");
        // nota sin hora de cierre
        assertRejectedSql("completed", "taken_by_id, taken_by_name, taken_at, closing_comment, "
                + "closing_author_id, closing_author_name",
                "(select id from users where username = 'tecnico'), 'Dueño', now(), 'Nota', "
                        + "(select id from users where username = 'tecnico'), 'Dueño'");
    }

    // --- Base propia: V7 + V7_1 -> V8 ------------------------------------------------------------------

    @Test
    void v8KeepsThe32SeededOrdersUntouched() {
        String databaseName = "invariants_it_" + UUID.randomUUID().toString().replace("-", "");
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

            Flyway.configure().dataSource(dataSource).locations(locations).load().migrate();

            assertThat(jdbc.queryForList(snapshot)).isEqualTo(before);
            assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname in "
                    + "('work_orders_owner_group_check', 'work_orders_closing_group_check', "
                    + "'work_orders_state_invariants_check')", Integer.class)).isEqualTo(3);
        } finally {
            admin.execute("drop database if exists " + databaseName + " with (force)");
        }
    }

    // --- Ayudas ----------------------------------------------------------------------------------------

    private void assertRejected(String status, boolean withOwner, boolean withNote) {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .as("%s owner=%s note=%s", status, withOwner, withNote)
                .isThrownBy(() -> insert(status, withOwner, withNote));
    }

    private void assertRejectedSql(String status, String columns, String values) {
        assertThatExceptionOfType(DataIntegrityViolationException.class).as("%s (%s)", status, columns)
                .isThrownBy(() -> jdbcTemplate.update("insert into work_orders (title, description, machine_id, "
                        + "breadcrumb, type, priority, status, created_at, " + columns + ") select 'WOINVIT-bad', "
                        + "'Descripción de prueba', 1, 'M', 'preventivo', 'low', ?, now(), " + values, status));
    }

    /** Inserta una fila coherente con las columnas pedidas; el autor del cierre, si hay nota, es el dueño. */
    private void insert(String status, boolean withOwner, boolean withNote) {
        String owner = withOwner ? "u.id, 'Dueño', now()" : "null, null, null";
        String note = withNote ? "'Nota', u.id, 'Dueño', now()" : "null, null, null, null";
        jdbcTemplate.update("insert into work_orders (title, description, machine_id, breadcrumb, type, priority, "
                + "status, created_at, taken_by_id, taken_by_name, taken_at, closing_comment, closing_author_id, "
                + "closing_author_name, closed_at) select 'WOINVIT-' || ?, 'Descripción de prueba', 1, 'M', "
                + "'preventivo', 'low', ?, now(), " + owner + ", " + note
                + " from users u where u.username = 'tecnico'", UUID.randomUUID().toString(), status);
    }
}
