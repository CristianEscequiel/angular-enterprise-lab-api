package com.enterpriselab.api.machines.persistence;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;
import com.enterpriselab.api.machines.domain.Machine;
import com.enterpriselab.api.machines.domain.MachineRepository;
import com.enterpriselab.api.shared.domain.ConflictException;
import com.enterpriselab.api.shared.domain.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-1, REQ-7, REQ-10, REQ-11, REQ-14, REQ-33: el adaptador contra Postgres real
 * y el seed de dev. Los códigos de prueba empiezan con {@code MREPO-} para no
 * chocar con el resto de las IT, que comparten la base, y se limpian al terminar;
 * las máquinas del seed solo se leen.
 */
@ActiveProfiles("dev")
class MachineRepositoryAdapterIT extends AbstractPostgresIT {

    @Autowired
    private MachineRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from parts where machine_id in (select id from machines where code like 'MREPO-%')");
        jdbcTemplate.update("delete from machines where code like 'MREPO-%'");
    }

    @Test
    void savesANewMachineAndReadsItBackWithZeroParts() {
        Machine saved = repository.save(new Machine(null, "MREPO-1", "Nueva", 0));

        assertThat(saved.id()).isNotNull();
        assertThat(saved).isEqualTo(new Machine(saved.id(), "MREPO-1", "Nueva", 0));
        assertThat(repository.findById(saved.id())).contains(saved);
        assertThat(repository.existsById(saved.id())).isTrue();
        assertThat(repository.existsById(-1L)).isFalse();
        assertThat(repository.findById(-1L)).isEmpty();
    }

    @Test
    void findAllReturnsTheSeededMachinesWithTheirPartCountInOrderOfCreation() {
        Machine created = repository.save(new Machine(null, "MREPO-1", "Nueva", 0));

        List<Machine> all = repository.findAll();

        assertThat(all).extracting(Machine::id).isSorted();
        assertThat(all.get(all.size() - 1)).isEqualTo(created);
        assertThat(all).contains(new Machine(1L, "ENV-01", "Envasadora línea 1", 7),
                new Machine(2L, "SEL-02", "Selladora", 3), new Machine(3L, "ROT-03", "Rotuladora", 0));
    }

    @Test
    void partCountCountsEveryLevelOfTheTree() {
        Machine machine = repository.save(new Machine(null, "MREPO-1", "Con partes", 0));
        Long root = insertPart(machine.id(), null);
        insertPart(machine.id(), root);

        assertThat(repository.findById(machine.id()).orElseThrow().partCount()).isEqualTo(2);
        assertThat(repository.countParts(machine.id())).isEqualTo(2);
        assertThat(repository.countParts(3L)).isZero();
    }

    @Test
    void savingAnExistingIdChangesCodeAndNameAndKeepsTheIdAndTheParts() {
        Machine created = repository.save(new Machine(null, "MREPO-1", "Vieja", 0));
        insertPart(created.id(), null);

        Machine updated = repository.save(new Machine(created.id(), "MREPO-2", "Nueva", 0));

        assertThat(updated).isEqualTo(new Machine(created.id(), "MREPO-2", "Nueva", 1));
        assertThat(repository.findById(created.id())).contains(updated);
    }

    @Test
    void existsByCodeAndIdNotExcludesTheMachineItself() {
        Machine first = repository.save(new Machine(null, "MREPO-1", "Una", 0));
        Machine second = repository.save(new Machine(null, "MREPO-2", "Otra", 0));

        assertThat(repository.existsByCode("MREPO-1")).isTrue();
        assertThat(repository.existsByCode("MREPO-9")).isFalse();
        assertThat(repository.existsByCodeAndIdNot("MREPO-1", first.id())).isFalse();
        assertThat(repository.existsByCodeAndIdNot("MREPO-1", second.id())).isTrue();
    }

    @Test
    void twoSavesOfTheSameCodeThatSkipTheServiceCheckAreADuplicateMachineCodeConflict() {
        repository.save(new Machine(null, "MREPO-1", "Una", 0));

        assertThatThrownBy(() -> repository.save(new Machine(null, "MREPO-1", "Otra", 0)))
                .isInstanceOfSatisfying(ConflictException.class, conflict -> {
                    assertThat(conflict.code()).isEqualTo("DUPLICATE_MACHINE_CODE");
                    assertThat(conflict.getMessage()).contains("MREPO-1");
                });
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from machines where code = 'MREPO-1'", Integer.class)).isEqualTo(1);
    }

    @Test
    void editingToTheCodeOfAnotherMachineIsADuplicateMachineCodeConflictAndKeepsTheMachine() {
        repository.save(new Machine(null, "MREPO-1", "Una", 0));
        Machine other = repository.save(new Machine(null, "MREPO-2", "Otra", 0));

        assertThatThrownBy(() -> repository.save(new Machine(other.id(), "MREPO-1", "Otra", 0)))
                .isInstanceOfSatisfying(ConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("DUPLICATE_MACHINE_CODE"));
        assertThat(repository.findById(other.id()).orElseThrow().code()).isEqualTo("MREPO-2");
    }

    @Test
    void savingAnUnknownIdIsNotFound() {
        assertThatThrownBy(() -> repository.save(new Machine(-1L, "MREPO-1", "X", 0)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletingAMachineThatGotAPartAtTheLastMomentIsAMachineHasPartsConflictAndKeepsEverything() {
        Machine machine = repository.save(new Machine(null, "MREPO-1", "Con partes", 0));
        insertPart(machine.id(), null);

        assertThatThrownBy(() -> repository.deleteById(machine.id()))
                .isInstanceOfSatisfying(ConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("MACHINE_HAS_PARTS"));
        assertThat(repository.existsById(machine.id())).isTrue();
        assertThat(repository.countParts(machine.id())).isEqualTo(1);
    }

    @Test
    void deletingAMachineWithoutPartsRemovesItAndAnUnknownIdIsANoOp() {
        Machine machine = repository.save(new Machine(null, "MREPO-1", "Sin partes", 0));

        repository.deleteById(machine.id());
        repository.deleteById(machine.id());

        assertThat(repository.existsById(machine.id())).isFalse();
    }

    @Test
    void anotherIntegrityViolationIsNotTranslated() {
        // Salteando la validación de dominio: rompe el CHECK machines_code_format, no el unique.
        assertThatThrownBy(() -> repository.save(new Machine(null, "mrepo bad", "X", 0)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(ConflictException.class);
    }

    private Long insertPart(Long machineId, Long parentId) {
        return jdbcTemplate.queryForObject(
                "insert into parts (machine_id, parent_id, name) values (?, ?, 'Parte') returning id",
                Long.class, machineId, parentId);
    }
}
