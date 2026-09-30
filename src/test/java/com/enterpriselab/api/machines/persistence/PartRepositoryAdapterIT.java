package com.enterpriselab.api.machines.persistence;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;
import com.enterpriselab.api.machines.domain.Part;
import com.enterpriselab.api.machines.domain.PartRepository;
import com.enterpriselab.api.shared.domain.ConflictException;
import com.enterpriselab.api.shared.domain.InvalidReferenceException;
import com.enterpriselab.api.shared.domain.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-16, REQ-24, REQ-27, REQ-28, REQ-33 y REQ-34: el adaptador contra Postgres
 * real y el seed de dev. Las máquinas de prueba empiezan con {@code PREPO-} y se
 * limpian al terminar; las del seed solo se leen.
 */
@ActiveProfiles("dev")
class PartRepositoryAdapterIT extends AbstractPostgresIT {

    @Autowired
    private PartRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long machineId;

    @BeforeEach
    void createMachine() {
        machineId = jdbcTemplate.queryForObject(
                "insert into machines (code, name) values ('PREPO-1', 'PartRepositoryAdapterIT') returning id",
                Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from parts where machine_id in (select id from machines where code like 'PREPO-%')");
        jdbcTemplate.update("delete from machines where code like 'PREPO-%'");
    }

    @Test
    void savesATopLevelPartAndASubPartAndReadsThemBackInOrderOfCreation() {
        Part root = repository.save(new Part(null, machineId, null, "Raíz"));
        Part child = repository.save(new Part(null, machineId, root.id(), "Hija"));

        assertThat(root).isEqualTo(new Part(root.id(), machineId, null, "Raíz"));
        assertThat(child).isEqualTo(new Part(child.id(), machineId, root.id(), "Hija"));
        assertThat(repository.findByMachineId(machineId)).containsExactly(root, child);
        assertThat(repository.findById(child.id())).contains(child);
        assertThat(repository.findById(-1L)).isEmpty();
    }

    @Test
    void findByMachineIdReturnsTheSeededEnvasadoraTreeAsAFlatList() {
        List<Part> parts = repository.findByMachineId(1L);

        assertThat(parts).extracting(Part::id).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(parts).extracting(Part::parentId).containsExactly(null, 1L, 2L, 3L, 1L, null, 6L);
        assertThat(repository.findByMachineId(3L)).isEmpty();
    }

    @Test
    void savingAnExistingIdChangesOnlyTheName() {
        Part root = repository.save(new Part(null, machineId, null, "Raíz"));
        Part child = repository.save(new Part(null, machineId, root.id(), "Hija"));

        // Aunque el llamador mande otra máquina o padre, una parte no se mueve (REQ-25).
        Part renamed = repository.save(new Part(child.id(), 1L, null, "Renombrada"));

        assertThat(renamed).isEqualTo(new Part(child.id(), machineId, root.id(), "Renombrada"));
        assertThat(repository.findById(child.id())).contains(renamed);
    }

    @Test
    void savingAnUnknownIdIsNotFound() {
        assertThatThrownBy(() -> repository.save(new Part(-1L, machineId, null, "X")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void countChildrenCountsOnlyDirectChildren() {
        Part root = repository.save(new Part(null, machineId, null, "Raíz"));
        Part child = repository.save(new Part(null, machineId, root.id(), "Hija"));
        repository.save(new Part(null, machineId, root.id(), "Hermana"));
        repository.save(new Part(null, machineId, child.id(), "Nieta"));

        assertThat(repository.countChildren(root.id())).isEqualTo(2);
        assertThat(repository.countChildren(child.id())).isEqualTo(1);
        assertThat(repository.countChildren(-1L)).isZero();
    }

    @Test
    void savingAPartWhoseParentWasDeletedAtTheLastMomentIsParentPartNotFound() {
        assertThatThrownBy(() -> repository.save(new Part(null, machineId, -1L, "Sin padre")))
                .isInstanceOfSatisfying(InvalidReferenceException.class,
                        failure -> assertThat(failure.code()).isEqualTo("PARENT_PART_NOT_FOUND"));
        assertThat(repository.findByMachineId(machineId)).isEmpty();
    }

    @Test
    void savingAPartOfAMachineDeletedAtTheLastMomentIsNotFound() {
        assertThatThrownBy(() -> repository.save(new Part(null, -1L, null, "Sin máquina")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletingAPartThatGotAChildAtTheLastMomentIsAPartHasChildrenConflictAndKeepsEverything() {
        Part root = repository.save(new Part(null, machineId, null, "Raíz"));
        Part child = repository.save(new Part(null, machineId, root.id(), "Hija"));

        assertThatThrownBy(() -> repository.deleteById(root.id()))
                .isInstanceOfSatisfying(ConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("PART_HAS_CHILDREN"));
        assertThat(repository.findByMachineId(machineId)).containsExactly(root, child);
    }

    @Test
    void aSubtreeIsDeletedFromTheLeavesUpAndAnUnknownIdIsANoOp() {
        Part root = repository.save(new Part(null, machineId, null, "Raíz"));
        Part child = repository.save(new Part(null, machineId, root.id(), "Hija"));

        repository.deleteById(child.id());
        repository.deleteById(root.id());
        repository.deleteById(root.id());

        assertThat(repository.findByMachineId(machineId)).isEmpty();
    }
}
