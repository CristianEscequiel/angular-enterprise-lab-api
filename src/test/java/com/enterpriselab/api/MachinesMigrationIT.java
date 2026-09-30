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
 * REQ-33: {@code V6} crea {@code machines} y {@code parts} con las garantías del
 * diseño (§2.1): código único y con formato, FK sin cascada hacia la máquina y
 * hacia el padre, y un padre que tiene que ser de la misma máquina. Sin perfil
 * {@code dev}, pero el contenedor se comparte con el resto de las IT, así que
 * cada test usa códigos propios ({@code MIGIT-*}) y limpia lo que creó.
 */
class MachinesMigrationIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        // Un solo DELETE por tabla: la FK del padre se comprueba al final de la sentencia.
        jdbcTemplate.update("delete from parts where machine_id in (select id from machines where code like 'MIGIT-%')");
        jdbcTemplate.update("delete from machines where code like 'MIGIT-%'");
    }

    @Test
    void machinesAndPartsTablesExistWithExpectedColumns() {
        assertThat(columns("machines")).containsExactlyInAnyOrder("id", "code", "name");
        assertThat(columns("parts")).containsExactlyInAnyOrder("id", "machine_id", "parent_id", "name");
    }

    @Test
    void theMachineCodeIsUnique() {
        insertMachine("MIGIT-A");

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                insertMachine("MIGIT-A"));
    }

    @Test
    void aMachineCodeThatIsNotNormalizedOrHasABadFormatIsRejected() {
        for (String code : List.of("migit-a", "-MIGIT", "MIGIT A", "MIGIT_A", "", "MIGIT-" + "X".repeat(20))) {
            assertThatExceptionOfType(DataIntegrityViolationException.class).as("código '%s'", code)
                    .isThrownBy(() -> insertMachine(code));
        }
    }

    @Test
    void aPartOfAMachineThatDoesNotExistViolatesTheForeignKey() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update("insert into parts (machine_id, parent_id, name) values (-1, null, 'Huérfana')"));
    }

    @Test
    void aPartWithAParentThatDoesNotExistViolatesTheForeignKey() {
        Long machineId = insertMachine("MIGIT-B");

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                insertPart(machineId, -1L, "Sin padre"));
    }

    @Test
    void aParentFromAnotherMachineViolatesTheSameMachineForeignKey() {
        Long machineA = insertMachine("MIGIT-C");
        Long machineB = insertMachine("MIGIT-D");
        Long partOfA = insertPart(machineA, null, "Parte de A");

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                insertPart(machineB, partOfA, "Hija cruzada")).withMessageContaining("parts_parent_same_machine_fkey");
    }

    @Test
    void aTopLevelPartAndASubPartOfTheSameMachineAreAccepted() {
        Long machineId = insertMachine("MIGIT-E");
        Long root = insertPart(machineId, null, "Raíz");

        Long child = insertPart(machineId, root, "Hija");

        assertThat(child).isNotNull();
    }

    @Test
    void deletingAMachineWithPartsViolatesTheForeignKey() {
        Long machineId = insertMachine("MIGIT-F");
        insertPart(machineId, null, "Parte");

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update("delete from machines where id = ?", machineId));
    }

    @Test
    void deletingAPartWithChildrenViolatesTheForeignKeyAndALeafCanBeDeleted() {
        Long machineId = insertMachine("MIGIT-G");
        Long root = insertPart(machineId, null, "Raíz");
        Long child = insertPart(machineId, root, "Hija");

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbcTemplate.update("delete from parts where id = ?", root));

        // De las hojas hacia arriba no hay cascada pero tampoco obstáculo.
        jdbcTemplate.update("delete from parts where id = ?", child);
        jdbcTemplate.update("delete from parts where id = ?", root);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from parts where machine_id = ?", Integer.class, machineId)).isZero();
    }

    private List<String> columns(String table) {
        return jdbcTemplate.queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ?",
                String.class, table);
    }

    private Long insertMachine(String code) {
        return jdbcTemplate.queryForObject(
                "insert into machines (code, name) values (?, 'MachinesMigrationIT') returning id",
                Long.class, code);
    }

    private Long insertPart(Long machineId, Long parentId, String name) {
        return jdbcTemplate.queryForObject(
                "insert into parts (machine_id, parent_id, name) values (?, ?, ?) returning id",
                Long.class, machineId, parentId, name);
    }
}
