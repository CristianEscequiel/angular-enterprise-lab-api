package com.enterpriselab.api.maintenance.persistence;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;
import com.enterpriselab.api.maintenance.domain.Specialty;
import com.enterpriselab.api.maintenance.domain.Technician;
import com.enterpriselab.api.maintenance.domain.TechnicianRepository;
import com.enterpriselab.api.maintenance.domain.TeamType;
import com.enterpriselab.api.shared.domain.ConflictException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-5, REQ-6, REQ-15, REQ-16, REQ-36: el adaptador contra Postgres real y el
 * seed de dev. Los legajos de prueba empiezan con {@code 9910} para no chocar
 * con el resto de las IT, que comparten la base, y se limpian al terminar.
 */
@ActiveProfiles("dev")
class TechnicianRepositoryAdapterIT extends AbstractPostgresIT {

    private static final String LEGAJO = "99100001";

    @Autowired
    private TechnicianRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from team_members where team_id in "
                + "(select id from teams where name = 'TechnicianRepositoryAdapterIT')");
        jdbcTemplate.update("delete from teams where name = 'TechnicianRepositoryAdapterIT'");
        jdbcTemplate.update("delete from technicians where legajo like '9910%'");
    }

    @Test
    void savesANewTechnicianAndReadsItBack() {
        Technician saved = repository.save(technician(LEGAJO));

        assertThat(saved.id()).isNotNull();
        assertThat(repository.findByLegajo(LEGAJO)).contains(saved);
        assertThat(repository.existsByLegajo(LEGAJO)).isTrue();
        assertThat(repository.existsByLegajo("99100999")).isFalse();
        assertThat(repository.findByLegajo("99100999")).isEmpty();
    }

    @Test
    void findAllReturnsTheSeededTechniciansInOrderOfCreation() {
        Technician created = repository.save(technician(LEGAJO));

        List<Technician> all = repository.findAll();

        assertThat(all).extracting(Technician::legajo).contains("1001", "1002", "1003", LEGAJO);
        assertThat(all).extracting(Technician::id).isSorted();
        assertThat(all.get(all.size() - 1)).isEqualTo(created);
    }

    @Test
    void savingAnExistingIdChangesTheFourFieldsAndKeepsIdAndLegajo() {
        Technician created = repository.save(technician(LEGAJO));

        Technician updated = repository.save(new Technician(created.id(), created.legajo(), "Luisa", "Paz",
                Specialty.ELECTRICISTA, TeamType.PREVENTIVO_CORRECTIVO));

        assertThat(updated).isEqualTo(new Technician(created.id(), LEGAJO, "Luisa", "Paz",
                Specialty.ELECTRICISTA, TeamType.PREVENTIVO_CORRECTIVO));
        assertThat(repository.findByLegajo(LEGAJO)).contains(updated);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from technicians where legajo = ?", Integer.class, LEGAJO)).isEqualTo(1);
    }

    @Test
    void findExistingLegajosReturnsOnlyTheOnesThatBelongToATechnician() {
        assertThat(repository.findExistingLegajos(List.of("1001", "99100999", "1002")))
                .isEqualTo(Set.of("1001", "1002"));
        assertThat(repository.findExistingLegajos(List.of())).isEmpty();
    }

    @Test
    void hasLoginUserIsTrueForSeededTechniciansWithLoginAndFalseOtherwise() {
        repository.save(technician(LEGAJO));

        assertThat(repository.hasLoginUser("1001")).isTrue();
        assertThat(repository.hasLoginUser("1002")).isTrue();
        assertThat(repository.hasLoginUser("1003")).isFalse();
        assertThat(repository.hasLoginUser(LEGAJO)).isFalse();
    }

    @Test
    void twoSavesOfTheSameLegajoThatSkipTheServiceCheckAreADuplicateLegajoConflict() {
        repository.save(technician(LEGAJO));

        assertThatThrownBy(() -> repository.save(technician(LEGAJO)))
                .isInstanceOfSatisfying(ConflictException.class, conflict -> {
                    assertThat(conflict.code()).isEqualTo("DUPLICATE_LEGAJO");
                    assertThat(conflict.getMessage()).contains(LEGAJO);
                });
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from technicians where legajo = ?", Integer.class, LEGAJO)).isEqualTo(1);
    }

    @Test
    void deletingATechnicianWithALoginIsATechnicianInUseConflictAndKeepsIt() {
        assertThatThrownBy(() -> repository.deleteByLegajo("1001"))
                .isInstanceOfSatisfying(ConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("TECHNICIAN_IN_USE"));
        assertThat(repository.existsByLegajo("1001")).isTrue();
    }

    @Test
    void deletingATechnicianWhoIsATeamMemberIsATechnicianInUseConflictAndKeepsIt() {
        repository.save(technician(LEGAJO));
        Long teamId = jdbcTemplate.queryForObject(
                "insert into teams (name, type) values ('TechnicianRepositoryAdapterIT', 'guardia') returning id",
                Long.class);
        jdbcTemplate.update("insert into team_members (team_id, technician_id, sort_order) "
                + "values (?, (select id from technicians where legajo = ?), 0)", teamId, LEGAJO);

        assertThatThrownBy(() -> repository.deleteByLegajo(LEGAJO))
                .isInstanceOfSatisfying(ConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("TECHNICIAN_IN_USE"));
        assertThat(repository.existsByLegajo(LEGAJO)).isTrue();
    }

    @Test
    void deletingATechnicianWithoutReferencesRemovesItAndAnUnknownLegajoIsANoOp() {
        repository.save(technician(LEGAJO));

        repository.deleteByLegajo(LEGAJO);
        repository.deleteByLegajo(LEGAJO);

        assertThat(repository.existsByLegajo(LEGAJO)).isFalse();
    }

    @Test
    void anotherIntegrityViolationIsNotTranslated() {
        // Salteando la validación de dominio: rompe el CHECK technicians_legajo_format, no el unique.
        assertThatThrownBy(() -> repository.save(technician("abc")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(ConflictException.class);
        assertThat(repository.existsByLegajo("abc")).isFalse();
    }

    private static Technician technician(String legajo) {
        return new Technician(null, legajo, "Ana", "Ruiz", Specialty.MECANICO, TeamType.GUARDIA);
    }
}
