package com.enterpriselab.api.workorders.persistence;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;
import com.enterpriselab.api.workorders.domain.MachineDirectory;
import com.enterpriselab.api.workorders.domain.PartLocation;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-23 y REQ-24: el adaptador del directorio contra Postgres real y el árbol del seed
 * de dev. Las máquinas de prueba llevan el código {@code MDIR-} y se limpian al
 * terminar.
 */
@ActiveProfiles("dev")
class MachineDirectoryAdapterIT extends AbstractPostgresIT {

    @Autowired
    private MachineDirectory directory;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from parts where machine_id in (select id from machines where code like 'MDIR-%')");
        jdbcTemplate.update("delete from machines where code like 'MDIR-%'");
    }

    @Test
    void findsTheNameOfAnExistingMachineAndNothingForAnUnknownOne() {
        assertThat(directory.findMachineName(1L)).contains("Envasadora línea 1");
        assertThat(directory.findMachineName(2L)).contains("Selladora");
        assertThat(directory.findMachineName(-1L)).isEmpty();
    }

    @Test
    void locatesATopLevelPartOfTheSeededTree() {
        assertThat(directory.locatePart(1L)).contains(new PartLocation(1L, List.of("Mesa de transporte")));
        assertThat(directory.locatePart(8L)).contains(new PartLocation(2L, List.of("Cabezal térmico")));
    }

    @Test
    void locatesThePartsOfTheSeededTreeWithTheirWholeChainInOrder() {
        assertThat(directory.locatePart(2L)).contains(
                new PartLocation(1L, List.of("Mesa de transporte", "Cinta 1")));
        assertThat(directory.locatePart(3L)).contains(
                new PartLocation(1L, List.of("Mesa de transporte", "Cinta 1", "Motor de cinta")));
        assertThat(directory.locatePart(4L)).contains(new PartLocation(1L,
                List.of("Mesa de transporte", "Cinta 1", "Motor de cinta", "Rodamiento delantero")));
        assertThat(directory.locatePart(7L)).contains(
                new PartLocation(1L, List.of("Cabezal de sellado", "Resistencia")));
    }

    @Test
    void theSameNameUnderDifferentParentsKeepsItsOwnChain() {
        // "Resistencia" existe bajo el cabezal de la Envasadora (7) y bajo el de la Selladora (9).
        assertThat(directory.locatePart(7L).orElseThrow().pathNames()).containsExactly("Cabezal de sellado", "Resistencia");
        assertThat(directory.locatePart(9L).orElseThrow().pathNames()).containsExactly("Cabezal térmico", "Resistencia");
        assertThat(directory.locatePart(9L).orElseThrow().machineId()).isEqualTo(2L);
    }

    @Test
    void locatesAPartAtTheFifthLevel() {
        long machineId = jdbcTemplate.queryForObject(
                "insert into machines (code, name) values ('MDIR-1', 'MachineDirectoryAdapterIT') returning id",
                Long.class);
        List<String> expected = new ArrayList<>();
        Long parent = null;
        Long last = null;
        for (int level = 1; level <= 5; level++) {
            last = jdbcTemplate.queryForObject(
                    "insert into parts (machine_id, parent_id, name) values (?, ?, ?) returning id",
                    Long.class, machineId, parent, "Nivel " + level);
            expected.add("Nivel " + level);
            parent = last;
        }

        assertThat(directory.locatePart(last)).contains(new PartLocation(machineId, expected));
    }

    @Test
    void anUnknownPartIsEmpty() {
        assertThat(directory.locatePart(-1L)).isEmpty();
        assertThat(directory.locatePart(987654321L)).isEmpty();
    }
}
