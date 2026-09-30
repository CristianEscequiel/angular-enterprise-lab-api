package com.enterpriselab.api.maintenance.domain;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enterpriselab.api.auth.domain.ForbiddenOperationException;
import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.shared.domain.InvalidReferenceException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.domain.ValidationFailedException;

import static com.enterpriselab.api.auth.domain.Role.TEAM_LEADER_MANTENIMIENTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    private static final Team EXISTING = new Team(1L, "Guardia mecánica", TeamType.GUARDIA, List.of("1001"));
    private static final TeamCommand VALID = new TeamCommand("Guardia mecánica", "guardia", List.of("1001", "1002"));

    @Mock
    private TeamRepository teams;
    @Mock
    private TechnicianRepository technicians;

    private TeamService service;

    @BeforeEach
    void setUp() {
        service = new TeamService(teams, technicians);
        // Caminos felices por defecto: el equipo 1 existe y todos los legajos existen.
        lenient().when(teams.findAll()).thenReturn(List.of(EXISTING));
        lenient().when(teams.findById(1L)).thenReturn(Optional.of(EXISTING));
        lenient().when(technicians.findExistingLegajos(anyCollection()))
                .thenAnswer(call -> new HashSet<String>(call.getArgument(0)));
        lenient().when(teams.save(any(Team.class))).thenAnswer(call -> call.getArgument(0));
    }

    // --- Permisos: un test por rol y acción (REQ-35) -----------------------------------------

    @ParameterizedTest
    @EnumSource(Role.class)
    void listIsForTeamLeaderOnly(Role role) {
        assertAllowedOnly(role, () -> service.list(role));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void getIsForTeamLeaderOnly(Role role) {
        assertAllowedOnly(role, () -> service.get(role, "1"));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void createIsForTeamLeaderOnly(Role role) {
        assertAllowedOnly(role, () -> service.create(role, VALID));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void updateIsForTeamLeaderOnly(Role role) {
        assertAllowedOnly(role, () -> service.update(role, "1", VALID));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void deleteIsForTeamLeaderOnly(Role role) {
        assertAllowedOnly(role, () -> service.delete(role, "1"));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMINISTRADOR", "PERSONAL_PRODUCCION", "TECNICO"})
    void forbiddenRolesGet403BeforeAnyValidationOrLookup(Role role) {
        TeamCommand invalid = new TeamCommand("", "x", null);

        assertThatThrownBy(() -> service.create(role, invalid)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.update(role, "abc", invalid)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.get(role, "abc")).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.delete(role, "abc")).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(teams, technicians);
    }

    // --- Consulta -------------------------------------------------------------------------------

    @Test
    void listAndGetReturnTheTeams() {
        assertThat(service.list(TEAM_LEADER_MANTENIMIENTO)).containsExactly(EXISTING);
        assertThat(service.get(TEAM_LEADER_MANTENIMIENTO, "1")).isEqualTo(EXISTING);
    }

    @Test
    void getOfAnUnknownIdIsNotFound() {
        assertThatThrownBy(() -> service.get(TEAM_LEADER_MANTENIMIENTO, "999")).isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", " 1", "1 ", "1.5", "-1", "+1", "1e3", "99999999999999999999"})
    void aNonNumericIdIsTreatedAsAnUnknownTeamWithoutLookingItUp(String id) {
        assertThatThrownBy(() -> service.get(TEAM_LEADER_MANTENIMIENTO, id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(TEAM_LEADER_MANTENIMIENTO, id, VALID))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(TEAM_LEADER_MANTENIMIENTO, id)).isInstanceOf(NotFoundException.class);
        verify(teams, never()).findById(anyLong());
        verify(teams, never()).save(any());
        verify(teams, never()).deleteById(anyLong());
    }

    // --- Alta -----------------------------------------------------------------------------------

    @Test
    void createSavesANewTeamWithoutIdKeepingTheMemberOrder() {
        TeamCommand command = new TeamCommand("Preventivo eléctrico", "preventivo-correctivo",
                List.of("1002", "1001"));

        Team created = service.create(TEAM_LEADER_MANTENIMIENTO, command);

        assertThat(created).isEqualTo(new Team(null, "Preventivo eléctrico", TeamType.PREVENTIVO_CORRECTIVO,
                List.of("1002", "1001")));
        verify(teams).save(created);
    }

    @Test
    void theSameLegajoCanBeMemberOfSeveralTeams() {
        service.create(TEAM_LEADER_MANTENIMIENTO, new TeamCommand("A", "guardia", List.of("1001")));
        service.create(TEAM_LEADER_MANTENIMIENTO, new TeamCommand("B", "guardia", List.of("1001")));

        verify(teams, times(2)).save(any(Team.class));
    }

    // --- Validación: cada caso contra create y contra update ---------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void aMissingEmptyOrBlankNameIsAValidationError(String name) {
        assertOnBothOperations(new TeamCommand(name, "guardia", List.of("1001")), thrown ->
                assertValidationKeys(thrown, "name"));
    }

    @Test
    void aNameOfExactly100CharactersIsAcceptedEvenWithPaddingAroundIt() {
        String exactly100 = "a".repeat(100);

        assertThatCode(() -> service.create(TEAM_LEADER_MANTENIMIENTO,
                new TeamCommand("  " + exactly100 + "  ", "guardia", List.of()))).doesNotThrowAnyException();
    }

    @Test
    void aNameOf101CharactersAfterTrimmingIsAValidationError() {
        assertOnBothOperations(new TeamCommand("  " + "a".repeat(101) + "  ", "guardia", List.of()), thrown ->
                assertValidationKeys(thrown, "name"));
    }

    @Test
    void theNameIsSavedTrimmedOnCreateAndOnUpdate() {
        TeamCommand padded = new TeamCommand("  Guardia nocturna\t", "guardia", List.of("1001"));

        service.create(TEAM_LEADER_MANTENIMIENTO, padded);
        service.update(TEAM_LEADER_MANTENIMIENTO, "1", padded);

        ArgumentCaptor<Team> saved = ArgumentCaptor.forClass(Team.class);
        verify(teams, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(team -> assertThat(team.name()).isEqualTo("Guardia nocturna"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Guardia", "GUARDIA", " guardia", "preventivo", "otro"})
    void aMissingOrInvalidTypeIsAValidationError(String type) {
        assertOnBothOperations(new TeamCommand("Equipo", type, List.of("1001")), thrown ->
                assertValidationKeys(thrown, "type"));
    }

    @Test
    void aMemberListWithAnInvalidFormatIsAValidationError() {
        for (List<String> members : List.<List<String>>of(List.of("abc"), List.of("123456789"), List.of(" 1"),
                List.of("1001", ""), Arrays.asList("1001", null))) {
            assertOnBothOperations(new TeamCommand("Equipo", "guardia", members), thrown ->
                    assertValidationKeys(thrown, "memberLegajos"));
        }
    }

    @Test
    void repeatedMembersAreAValidationError() {
        assertOnBothOperations(new TeamCommand("Equipo", "guardia", List.of("1001", "1002", "1001")), thrown ->
                assertValidationKeys(thrown, "memberLegajos"));
    }

    @Test
    void legajosThatOnlyDifferInLeadingZerosAreNotRepeated() {
        assertThatCode(() -> service.create(TEAM_LEADER_MANTENIMIENTO,
                new TeamCommand("Equipo", "guardia", List.of("0001", "1")))).doesNotThrowAnyException();
    }

    @Test
    void aMissingMemberListIsAValidationErrorAndAnEmptyOneIsAccepted() {
        assertOnBothOperations(new TeamCommand("Equipo", "guardia", null), thrown ->
                assertValidationKeys(thrown, "memberLegajos"));

        Team created = service.create(TEAM_LEADER_MANTENIMIENTO, new TeamCommand("Equipo", "guardia", List.of()));
        Team updated = service.update(TEAM_LEADER_MANTENIMIENTO, "1", new TeamCommand("Equipo", "guardia", List.of()));

        assertThat(created.memberLegajos()).isEmpty();
        assertThat(updated.memberLegajos()).isEmpty();
        verify(technicians, never()).findExistingLegajos(anyCollection());
    }

    @Test
    void everyInvalidFieldIsReportedAtOnceAndTheTechniciansAreNotLookedUp() {
        assertOnBothOperations(new TeamCommand("", "x", List.of("abc")), thrown ->
                assertValidationKeys(thrown, "name", "type", "memberLegajos"));
        verify(technicians, never()).findExistingLegajos(anyCollection());
    }

    @Test
    void unknownMembersAreABadReferenceListingAllOfThem() {
        when(technicians.findExistingLegajos(anyCollection())).thenReturn(Set.of("1001"));
        TeamCommand command = new TeamCommand("Equipo", "guardia", List.of("1001", "9998", "9999"));

        assertOnBothOperations(command, thrown -> assertThat(thrown).isInstanceOfSatisfying(
                InvalidReferenceException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("UNKNOWN_TECHNICIAN");
                    assertThat(exception.getMessage()).contains("9998", "9999").doesNotContain("1001");
                }));
    }

    @Test
    void aSingleUnknownMemberIsNamedInTheMessage() {
        when(technicians.findExistingLegajos(anyCollection())).thenReturn(Set.of());

        Throwable thrown = catchThrowable(() -> service.create(TEAM_LEADER_MANTENIMIENTO,
                new TeamCommand("Equipo", "guardia", List.of("9999"))));

        assertThat(thrown).isInstanceOfSatisfying(InvalidReferenceException.class, exception ->
                assertThat(exception.getMessage()).isEqualTo("No existe el técnico con legajo 9999"));
    }

    // --- Edición --------------------------------------------------------------------------------

    @Test
    void updateReplacesNameTypeAndMembersKeepingTheId() {
        TeamCommand command = new TeamCommand("Preventivo eléctrico", "preventivo-correctivo",
                List.of("1002", "1001"));

        Team updated = service.update(TEAM_LEADER_MANTENIMIENTO, "1", command);

        assertThat(updated).isEqualTo(new Team(1L, "Preventivo eléctrico", TeamType.PREVENTIVO_CORRECTIVO,
                List.of("1002", "1001")));
        verify(teams).save(updated);
    }

    @Test
    void updateOfAnUnknownIdIsNotFoundAndDoesNotSave() {
        assertThatThrownBy(() -> service.update(TEAM_LEADER_MANTENIMIENTO, "999", VALID))
                .isInstanceOf(NotFoundException.class);
        verify(teams, never()).save(any());
    }

    @Test
    void anInvalidBodyOnAnUnknownIdIsAValidationErrorNotANotFound() {
        assertThatThrownBy(() -> service.update(TEAM_LEADER_MANTENIMIENTO, "999", new TeamCommand("", "guardia", List.of())))
                .isInstanceOf(ValidationFailedException.class);
        verify(teams, never()).findById(anyLong());
    }

    @Test
    void anUnknownIdIsANotFoundEvenIfTheMembersDoNotExist() {
        lenient().when(technicians.findExistingLegajos(anyCollection())).thenReturn(Set.of());

        assertThatThrownBy(() -> service.update(TEAM_LEADER_MANTENIMIENTO, "999", VALID))
                .isInstanceOf(NotFoundException.class);
        verify(technicians, never()).findExistingLegajos(anyCollection());
    }

    // --- Baja -----------------------------------------------------------------------------------

    @Test
    void deleteRemovesTheTeamById() {
        service.delete(TEAM_LEADER_MANTENIMIENTO, "1");

        verify(teams).deleteById(1L);
    }

    @Test
    void deleteOfAnUnknownIdIsNotFound() {
        assertThatThrownBy(() -> service.delete(TEAM_LEADER_MANTENIMIENTO, "999"))
                .isInstanceOf(NotFoundException.class);
        verify(teams, never()).deleteById(anyLong());
    }

    // --- Helpers --------------------------------------------------------------------------------

    /**
     * Corre el mismo comando contra {@code create} y contra {@code update} (REQ-26 a REQ-28 y REQ-31),
     * exige que ambos fallen como indica {@code assertion} y que ninguno guarde nada.
     */
    private void assertOnBothOperations(TeamCommand command, Consumer<Throwable> assertion) {
        for (Function<TeamCommand, Object> operation : List.<Function<TeamCommand, Object>>of(
                c -> service.create(TEAM_LEADER_MANTENIMIENTO, c),
                c -> service.update(TEAM_LEADER_MANTENIMIENTO, "1", c))) {
            assertion.accept(catchThrowable(() -> operation.apply(command)));
        }
        verify(teams, never()).save(any());
    }

    private static void assertValidationKeys(Throwable thrown, String... keys) {
        assertThat(thrown).isInstanceOfSatisfying(ValidationFailedException.class, exception ->
                assertThat(exception.details()).containsOnlyKeys(keys));
    }

    private void assertAllowedOnly(Role role, Runnable operation) {
        if (role == TEAM_LEADER_MANTENIMIENTO) {
            assertThatCode(operation::run).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(operation::run).isInstanceOf(ForbiddenOperationException.class);
            verifyNoInteractions(teams, technicians);
        }
    }
}
