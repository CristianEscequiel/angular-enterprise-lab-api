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
 * REQ-43: {@code V7} crea {@code work_orders} con {@code CHECK} sobre tipo,
 * prioridad y estado, y sin clave foránea hacia {@code machines} ni
 * {@code parts} (REQ-30). Sin perfil {@code dev}, pero el contenedor se comparte
 * con el resto de las IT, así que cada test usa títulos propios
 * ({@code WOMIGIT-*}) y limpia lo que creó.
 */
class WorkOrdersMigrationIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from work_orders where title like 'WOMIGIT-%'");
    }

    @Test
    void theTableExistsWithTheExpectedColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name = 'work_orders'", String.class);

        assertThat(columns).containsExactlyInAnyOrder("id", "title", "description", "machine_id", "part_id",
                "breadcrumb", "machine_comment", "type", "priority", "status", "created_at", "taken_by_id",
                "taken_by_name", "taken_at", "closing_comment", "closing_author_id", "closing_author_name",
                "closed_at");
    }

    @Test
    void aValidRowIsAcceptedAndTheCommentDefaultsToEmpty() {
        insert("WOMIGIT-ok", "preventivo", "low", "pending");

        assertThat(jdbcTemplate.queryForObject(
                "select machine_comment from work_orders where title = 'WOMIGIT-ok'", String.class)).isEmpty();
    }

    @Test
    void anInvalidTypeIsRejected() {
        for (String type : List.of("otro", "Preventivo", "")) {
            assertThatExceptionOfType(DataIntegrityViolationException.class).as("type '%s'", type)
                    .isThrownBy(() -> insert("WOMIGIT-type", type, "low", "pending"));
        }
    }

    @Test
    void anInvalidPriorityIsRejected() {
        for (String priority : List.of("urgent", "High", "")) {
            assertThatExceptionOfType(DataIntegrityViolationException.class).as("priority '%s'", priority)
                    .isThrownBy(() -> insert("WOMIGIT-priority", "preventivo", priority, "pending"));
        }
    }

    @Test
    void anInvalidStatusIsRejected() {
        for (String status : List.of("open", "In-Progress", "")) {
            assertThatExceptionOfType(DataIntegrityViolationException.class).as("status '%s'", status)
                    .isThrownBy(() -> insert("WOMIGIT-status", "preventivo", "low", status));
        }
    }

    @Test
    void everyValidValueIsAccepted() {
        for (String type : List.of("preventivo", "correctivo", "pronto-intervencion")) {
            insert("WOMIGIT-" + type, type, "medium", "pending");
        }
        for (String priority : List.of("low", "medium", "high")) {
            insert("WOMIGIT-p-" + priority, "preventivo", priority, "pending");
        }
        insert("WOMIGIT-s-pending", "preventivo", "low", "pending");
        for (String status : List.of("in-progress", "completed", "cancelled")) {
            insertWithOwnerAndNote("WOMIGIT-s-" + status, status);
        }

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from work_orders where title like 'WOMIGIT-%'", Integer.class)).isEqualTo(10);
    }

    @Test
    void theMachineAndThePartAreNotForeignKeys() {
        // REQ-30: ids que no existen en machines ni parts se aceptan; la orden conserva su historia.
        jdbcTemplate.update("insert into work_orders (title, description, machine_id, part_id, breadcrumb, type, "
                + "priority, status, created_at) values ('WOMIGIT-refs', 'Descripción de prueba', "
                + "987654321, 987654322, 'Máquina borrada > Parte borrada', 'correctivo', 'low', 'pending', now())");

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from work_orders where title = 'WOMIGIT-refs'", Integer.class)).isEqualTo(1);
    }

    @Test
    void theOwnerAndTheClosingAuthorMustBeRealUsers() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update("insert into work_orders (title, description, machine_id, breadcrumb, type, "
                        + "priority, status, created_at, taken_by_id) values ('WOMIGIT-owner', "
                        + "'Descripción de prueba', 1, 'M', 'correctivo', 'low', 'in-progress', now(), -1)"));
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update("insert into work_orders (title, description, machine_id, breadcrumb, type, "
                        + "priority, status, created_at, closing_author_id) values ('WOMIGIT-author', "
                        + "'Descripción de prueba', 1, 'M', 'correctivo', 'low', 'completed', now(), -1)"));
    }

    @Test
    void anOverlongTitleOrDescriptionIsRejected() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                insert("WOMIGIT-" + "t".repeat(150), "preventivo", "low", "pending"));
    }

    private void insert(String title, String type, String priority, String status) {
        jdbcTemplate.update("insert into work_orders (title, description, machine_id, breadcrumb, type, priority, "
                + "status, created_at) values (?, 'Descripción de prueba', 1, 'Envasadora', ?, ?, ?, now())",
                title, type, priority, status);
    }

    /** V8 (spec 04) exige dueño en las órdenes en progreso o cerradas, y nota en las cerradas. */
    private void insertWithOwnerAndNote(String title, String status) {
        boolean closed = status.equals("completed") || status.equals("cancelled");
        jdbcTemplate.update("insert into work_orders (title, description, machine_id, breadcrumb, type, priority, "
                + "status, created_at, taken_by_id, taken_by_name, taken_at, closing_comment, closing_author_id, "
                + "closing_author_name, closed_at) select ?, 'Descripción de prueba', 1, 'Envasadora', "
                + "'preventivo', 'low', ?, now(), u.id, 'Dueño', now(), "
                + (closed ? "'Nota de cierre', u.id, 'Dueño', now()" : "null, null, null, null")
                + " from users u where u.username = 'tecnico'", title, status);
    }
}
