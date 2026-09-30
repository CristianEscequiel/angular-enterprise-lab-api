package com.enterpriselab.api.maintenance.persistence;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;
import com.enterpriselab.api.maintenance.domain.Team;
import com.enterpriselab.api.maintenance.domain.TeamRepository;
import com.enterpriselab.api.maintenance.domain.TeamType;
import com.enterpriselab.api.shared.domain.InvalidReferenceException;
import com.enterpriselab.api.shared.domain.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-16, REQ-29, REQ-30, REQ-31, REQ-33, REQ-36: el adaptador contra Postgres
 * real y el seed de dev. Los equipos de prueba llevan el prefijo
 * {@code TeamRepoIT} para no chocar con el resto de las IT (la base se
 * comparte) y se limpian al terminar; sus membresías caen por el
 * {@code ON DELETE CASCADE}.
 */
@ActiveProfiles("dev")
class TeamRepositoryAdapterIT extends AbstractPostgresIT {

    private static final String NAME = "TeamRepoIT";

    @Autowired
    private TeamRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from teams where name like 'TeamRepoIT%'");
    }

    @Test
    void savesANewTeamAndReadsItBackWithTheMembersInTheSavedOrder() {
        Team saved = repository.save(new Team(null, NAME, TeamType.GUARDIA, List.of("1002", "1001")));

        assertThat(saved.id()).isNotNull();
        assertThat(repository.findById(saved.id())).contains(saved);
        assertThat(repository.findById(saved.id()).orElseThrow().memberLegajos()).containsExactly("1002", "1001");
        assertThat(sortOrders(saved.id())).containsExactly(0, 1);
    }

    @Test
    void findAllReturnsTheSeededTeamsWithTheirMembersInOrderOfCreation() {
        Team created = repository.save(new Team(null, NAME, TeamType.PREVENTIVO_CORRECTIVO, List.of("1003", "1001")));

        List<Team> all = repository.findAll();

        assertThat(all).extracting(Team::name).contains("Guardia mecánica", "Preventivo eléctrico", NAME);
        assertThat(all).extracting(Team::id).isSorted();
        assertThat(team(all, "Guardia mecánica").memberLegajos()).containsExactly("1001");
        assertThat(team(all, "Preventivo eléctrico").memberLegajos()).containsExactly("1002");
        assertThat(team(all, NAME)).isEqualTo(created);
    }

    @Test
    void anUnknownIdIsEmpty() {
        assertThat(repository.findById(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void replacingKeepsOneMemberDropsAnotherAndReorders() {
        Team created = repository.save(new Team(null, NAME, TeamType.GUARDIA, List.of("1001", "1002", "1003")));

        // Conserva 1001 y 1003, saca 1002 y los reordena: con orphanRemoval esto choca con el unique.
        Team replaced = repository.save(new Team(created.id(), NAME + "-2", TeamType.PREVENTIVO_CORRECTIVO,
                List.of("1003", "1001")));

        assertThat(replaced).isEqualTo(new Team(created.id(), NAME + "-2", TeamType.PREVENTIVO_CORRECTIVO,
                List.of("1003", "1001")));
        assertThat(repository.findById(created.id())).contains(replaced);
        assertThat(sortOrders(created.id())).containsExactly(0, 1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from teams where name like 'TeamRepoIT%'", Integer.class)).isEqualTo(1);
    }

    @Test
    void replacingWithAnEmptyListLeavesTheTeamWithoutMembers() {
        Team created = repository.save(new Team(null, NAME, TeamType.GUARDIA, List.of("1001")));

        Team replaced = repository.save(new Team(created.id(), NAME, TeamType.GUARDIA, List.of()));

        assertThat(replaced.memberLegajos()).isEmpty();
        assertThat(repository.findById(created.id()).orElseThrow().memberLegajos()).isEmpty();
        assertThat(sortOrders(created.id())).isEmpty();
    }

    @Test
    void theSameLegajoCanBeMemberOfSeveralTeams() {
        repository.save(new Team(null, NAME + "-A", TeamType.GUARDIA, List.of("1001")));
        repository.save(new Team(null, NAME + "-B", TeamType.GUARDIA, List.of("1001")));

        assertThat(repository.findNamesByMemberLegajo("1001"))
                .contains("Guardia mecánica", NAME + "-A", NAME + "-B");
    }

    @Test
    void findNamesByMemberLegajoIsEmptyForATechnicianWithoutTeams() {
        assertThat(repository.findNamesByMemberLegajo("1003")).isEmpty();
        assertThat(repository.findNamesByMemberLegajo("99200999")).isEmpty();
        assertThat(repository.findNamesByMemberLegajo("1002")).containsExactly("Preventivo eléctrico");
    }

    @Test
    void deletingATeamRemovesItsMembershipsAndKeepsTheTechnicians() {
        Team created = repository.save(new Team(null, NAME, TeamType.GUARDIA, List.of("1001", "1002")));

        repository.deleteById(created.id());

        assertThat(repository.findById(created.id())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from team_members where team_id = ?", Integer.class, created.id())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from technicians where legajo in ('1001', '1002')", Integer.class)).isEqualTo(2);
    }

    @Test
    void deletingAnUnknownIdIsANoOp() {
        assertThatCode(() -> repository.deleteById(Long.MAX_VALUE)).doesNotThrowAnyException();
    }

    @Test
    void aMemberWhoNoLongerExistsIsABadReferenceAndTheWholeSaveIsRolledBack() {
        Team created = repository.save(new Team(null, NAME, TeamType.GUARDIA, List.of("1001")));

        assertThatThrownBy(() -> repository.save(new Team(created.id(), NAME + "-2", TeamType.PREVENTIVO_CORRECTIVO,
                List.of("1002", "99200999"))))
                .isInstanceOfSatisfying(InvalidReferenceException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("UNKNOWN_TECHNICIAN");
                    assertThat(exception.getMessage()).contains("99200999");
                });

        // Todo o nada: ni nombre, ni tipo, ni miembros cambiaron.
        assertThat(repository.findById(created.id())).contains(created);
        assertThatThrownBy(() -> repository.save(new Team(null, NAME + "-3", TeamType.GUARDIA, List.of("99200999"))))
                .isInstanceOf(InvalidReferenceException.class);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from teams where name = ?", Integer.class, NAME + "-3")).isZero();
    }

    @Test
    void savingAnUnknownIdIsNotFound() {
        assertThatThrownBy(() -> repository.save(new Team(Long.MAX_VALUE, NAME, TeamType.GUARDIA, List.of())))
                .isInstanceOf(NotFoundException.class);
    }

    private static Team team(List<Team> teams, String name) {
        Optional<Team> found = teams.stream().filter(team -> team.name().equals(name)).findFirst();
        return found.orElseThrow(() -> new AssertionError("No está el equipo " + name));
    }

    private List<Integer> sortOrders(Long teamId) {
        return jdbcTemplate.queryForList(
                "select sort_order from team_members where team_id = ? order by sort_order", Integer.class, teamId);
    }
}
