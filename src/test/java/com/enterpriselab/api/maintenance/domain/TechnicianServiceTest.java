package com.enterpriselab.api.maintenance.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import com.enterpriselab.api.shared.domain.ConflictException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.domain.ValidationFailedException;

import static com.enterpriselab.api.auth.domain.Role.ADMINISTRADOR;
import static com.enterpriselab.api.auth.domain.Role.TEAM_LEADER_MANTENIMIENTO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TechnicianServiceTest {

    private static final TechnicianCommand VALID = new TechnicianCommand(
            "2001", "Ana", "Ruiz", "mecanico", "guardia");
    private static final Technician EXISTING = new Technician(7L, "2001", "Ana", "Ruiz",
            Specialty.MECANICO, TeamType.GUARDIA);

    @Mock
    private TechnicianRepository technicians;
    @Mock
    private TeamRepository teams;

    private TechnicianService service;

    @BeforeEach
    void setUp() {
        service = new TechnicianService(technicians, teams);
        // Caminos felices por defecto, para que una operación permitida termine sin error.
        lenient().when(technicians.findAll()).thenReturn(List.of(EXISTING));
        lenient().when(technicians.findByLegajo("2001")).thenReturn(Optional.of(EXISTING));
        lenient().when(technicians.existsByLegajo(any())).thenReturn(false);
        lenient().when(technicians.hasLoginUser(any())).thenReturn(false);
        lenient().when(teams.findNamesByMemberLegajo(any())).thenReturn(List.of());
        lenient().when(technicians.save(any(Technician.class))).thenAnswer(call -> call.getArgument(0));
    }

    // --- Permisos: un test por rol y acción (REQ-18, REQ-19) -------------------------------------

    @ParameterizedTest
    @EnumSource(Role.class)
    void listIsForAdministratorAndTeamLeader(Role role) {
        assertAllowedOnly(Set.of(ADMINISTRADOR, TEAM_LEADER_MANTENIMIENTO), role, () -> service.list(role));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void getIsForAdministratorAndTeamLeader(Role role) {
        assertAllowedOnly(Set.of(ADMINISTRADOR, TEAM_LEADER_MANTENIMIENTO), role,
                () -> service.get(role, "2001"));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void createIsForAdministratorAndTeamLeader(Role role) {
        assertAllowedOnly(Set.of(ADMINISTRADOR, TEAM_LEADER_MANTENIMIENTO), role,
                () -> service.create(role, VALID));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void updateIsForAdministratorAndTeamLeader(Role role) {
        assertAllowedOnly(Set.of(ADMINISTRADOR, TEAM_LEADER_MANTENIMIENTO), role,
                () -> service.update(role, "2001", VALID));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void deleteIsForAdministratorOnly(Role role) {
        assertAllowedOnly(Set.of(ADMINISTRADOR), role, () -> service.delete(role, "2001"));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"PERSONAL_PRODUCCION", "TECNICO"})
    void forbiddenRolesGet403EvenWithAMalformedLegajoInTheUrl(Role role) {
        assertThatThrownBy(() -> service.get(role, "abc")).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.update(role, "abc", VALID)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.delete(role, "abc")).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(technicians, teams);
    }

    @Test
    void teamLeaderCannotDeleteEvenWithAMalformedLegajoInTheUrl() {
        assertThatThrownBy(() -> service.delete(TEAM_LEADER_MANTENIMIENTO, "abc"))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(technicians, teams);
    }

    // --- Consulta -------------------------------------------------------------------------------

    @Test
    void getReturnsTheTechnician() {
        assertThat(service.get(ADMINISTRADOR, "2001")).isEqualTo(EXISTING);
    }

    @Test
    void getOfAnUnknownLegajoIsNotFound() {
        assertThatThrownBy(() -> service.get(ADMINISTRADOR, "9999")).isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "123456789", " 1"})
    void aMalformedLegajoInTheUrlIsAValidationErrorAndNeverReachesTheRepositories(String legajo) {
        assertLegajoError(catchThrowable(() -> service.get(ADMINISTRADOR, legajo)));
        assertLegajoError(catchThrowable(() -> service.update(ADMINISTRADOR, legajo, VALID)));
        assertLegajoError(catchThrowable(() -> service.delete(ADMINISTRADOR, legajo)));
        verifyNoInteractions(technicians, teams);
    }

    // --- Alta -----------------------------------------------------------------------------------

    @Test
    void createSavesANewTechnicianWithoutIdAndReturnsIt() {
        Technician created = service.create(TEAM_LEADER_MANTENIMIENTO, VALID);

        ArgumentCaptor<Technician> saved = ArgumentCaptor.forClass(Technician.class);
        verify(technicians).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(
                new Technician(null, "2001", "Ana", "Ruiz", Specialty.MECANICO, TeamType.GUARDIA));
        assertThat(created).isEqualTo(saved.getValue());
    }

    @Test
    void createWithADuplicatedLegajoIsAConflictAndDoesNotSave() {
        when(technicians.existsByLegajo("2001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(ADMINISTRADOR, VALID))
                .isInstanceOfSatisfying(ConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("DUPLICATE_LEGAJO"));
        verify(technicians, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "123456789", " 1001", "1001 "})
    void createWithAnInvalidLegajoIsAValidationErrorOnLegajo(String legajo) {
        TechnicianCommand command = new TechnicianCommand(legajo, "Ana", "Ruiz", "mecanico", "guardia");

        assertLegajoError(catchThrowable(() -> service.create(ADMINISTRADOR, command)));
        verify(technicians, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void createWithAMissingEmptyOrBlankNameIsAValidationError(String blank) {
        assertThat(errorsOf(new TechnicianCommand("2001", blank, "Ruiz", "mecanico", "guardia")))
                .containsExactly(Map.entry("firstName", TechnicianService.REQUIRED_MESSAGE));
        assertThat(errorsOf(new TechnicianCommand("2001", "Ana", blank, "mecanico", "guardia")))
                .containsExactly(Map.entry("lastName", TechnicianService.REQUIRED_MESSAGE));
        verify(technicians, never()).save(any());
    }

    @Test
    void namesAreCappedAt100CharactersAfterTrimming() {
        String exactly100 = "a".repeat(100);
        assertThatCode(() -> service.create(ADMINISTRADOR,
                new TechnicianCommand("2001", "  " + exactly100 + "  ", exactly100, "mecanico", "guardia")))
                .doesNotThrowAnyException();

        String tooLong = "a".repeat(101);
        assertThat(errorsOf(new TechnicianCommand("2001", tooLong, "Ruiz", "mecanico", "guardia")))
                .containsExactly(Map.entry("firstName", TechnicianService.NAME_TOO_LONG_MESSAGE));
        assertThat(errorsOf(new TechnicianCommand("2001", "Ana", tooLong, "mecanico", "guardia")))
                .containsExactly(Map.entry("lastName", TechnicianService.NAME_TOO_LONG_MESSAGE));
    }

    @Test
    void namesAreSavedTrimmedOnCreateAndOnUpdate() {
        TechnicianCommand padded = new TechnicianCommand(null, "  Ana María ", "\tRuiz  ", "general", "guardia");

        service.create(ADMINISTRADOR, new TechnicianCommand("2001", "  Ana María ", "\tRuiz  ", "general", "guardia"));
        service.update(ADMINISTRADOR, "2001", padded);

        ArgumentCaptor<Technician> saved = ArgumentCaptor.forClass(Technician.class);
        verify(technicians, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(technician -> {
            assertThat(technician.firstName()).isEqualTo("Ana María");
            assertThat(technician.lastName()).isEqualTo("Ruiz");
        });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Mecanico", "MECANICO", " mecanico", "plomero"})
    void createWithAMissingOrInvalidSpecialtyIsAValidationError(String specialty) {
        Map<String, String> errors = errorsOf(new TechnicianCommand("2001", "Ana", "Ruiz", specialty, "guardia"));

        assertThat(errors).containsOnlyKeys("specialty");
        assertThat(errors.get("specialty")).isNotBlank();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Guardia", "GUARDIA", "guardia ", "preventivo"})
    void createWithAMissingOrInvalidTeamTypeIsAValidationError(String teamType) {
        Map<String, String> errors = errorsOf(new TechnicianCommand("2001", "Ana", "Ruiz", "mecanico", teamType));

        assertThat(errors).containsOnlyKeys("teamType");
        assertThat(errors.get("teamType")).isNotBlank();
    }

    @Test
    void createReportsEveryInvalidFieldAtOnce() {
        Map<String, String> errors = errorsOf(new TechnicianCommand("abc", "", null, "plomero", null));

        assertThat(errors).containsOnlyKeys("legajo", "firstName", "lastName", "specialty", "teamType");
    }

    // --- Edición --------------------------------------------------------------------------------

    @Test
    void updateChangesTheFourFieldsAndKeepsIdAndLegajo() {
        TechnicianCommand command = new TechnicianCommand(null, "Luisa", "Paz", "electricista", "preventivo-correctivo");

        Technician updated = service.update(ADMINISTRADOR, "2001", command);

        assertThat(updated).isEqualTo(new Technician(7L, "2001", "Luisa", "Paz",
                Specialty.ELECTRICISTA, TeamType.PREVENTIVO_CORRECTIVO));
        verify(technicians).save(updated);
    }

    @Test
    void updateAcceptsABodyLegajoEqualToTheUrlOne() {
        assertThatCode(() -> service.update(ADMINISTRADOR, "2001", VALID)).doesNotThrowAnyException();
    }

    @Test
    void updateWithADifferentBodyLegajoIsAValidationErrorAndDoesNotSave() {
        TechnicianCommand command = new TechnicianCommand("3001", "Ana", "Ruiz", "mecanico", "guardia");

        assertThatThrownBy(() -> service.update(ADMINISTRADOR, "2001", command))
                .isInstanceOfSatisfying(ValidationFailedException.class, exception ->
                        assertThat(exception.details()).containsOnlyKeys("legajo"));
        verify(technicians, never()).save(any());
    }

    @Test
    void updateOfAnUnknownLegajoIsNotFoundAndDoesNotSave() {
        // Sin legajo en el cuerpo: si trajera el de VALID ("2001") sería un 400 por legajo distinto.
        TechnicianCommand withoutLegajo = new TechnicianCommand(null, "Ana", "Ruiz", "mecanico", "guardia");

        assertThatThrownBy(() -> service.update(ADMINISTRADOR, "9999", withoutLegajo))
                .isInstanceOf(NotFoundException.class);
        verify(technicians, never()).save(any());
    }

    @Test
    void updateWithAnInvalidBodyOnAnUnknownLegajoIsAValidationErrorNotANotFound() {
        TechnicianCommand invalid = new TechnicianCommand(null, "", "Ruiz", "mecanico", "guardia");

        assertThatThrownBy(() -> service.update(ADMINISTRADOR, "9999", invalid))
                .isInstanceOf(ValidationFailedException.class);
        verify(technicians, never()).findByLegajo(any());
    }

    // --- Baja -----------------------------------------------------------------------------------

    @Test
    void deleteRemovesATechnicianWithoutLoginAndWithoutTeams() {
        service.delete(ADMINISTRADOR, "2001");

        verify(technicians).deleteByLegajo("2001");
    }

    @Test
    void deleteOfAnUnknownLegajoIsNotFound() {
        assertThatThrownBy(() -> service.delete(ADMINISTRADOR, "9999")).isInstanceOf(NotFoundException.class);
        verify(technicians, never()).deleteByLegajo(any());
    }

    @Test
    void deleteIsBlockedByALoginUser() {
        when(technicians.hasLoginUser("2001")).thenReturn(true);

        assertThatThrownBy(() -> service.delete(ADMINISTRADOR, "2001")).isInstanceOfSatisfying(
                ConflictException.class, conflict -> {
                    assertThat(conflict.code()).isEqualTo("TECHNICIAN_IN_USE");
                    assertThat(conflict.getMessage()).contains("usuario de acceso").doesNotContain("miembro");
                });
        verify(technicians, never()).deleteByLegajo(any());
    }

    @Test
    void deleteIsBlockedByTeamMembershipAndNamesTheTeams() {
        when(teams.findNamesByMemberLegajo("2001")).thenReturn(List.of("Guardia mecánica", "Preventivo eléctrico"));

        assertThatThrownBy(() -> service.delete(ADMINISTRADOR, "2001")).isInstanceOfSatisfying(
                ConflictException.class, conflict -> {
                    assertThat(conflict.code()).isEqualTo("TECHNICIAN_IN_USE");
                    assertThat(conflict.getMessage()).contains("Guardia mecánica", "Preventivo eléctrico")
                            .doesNotContain("usuario de acceso");
                });
        verify(technicians, never()).deleteByLegajo(any());
    }

    @Test
    void deleteWithBothReasonsListsThemInASingleConflict() {
        when(technicians.hasLoginUser("2001")).thenReturn(true);
        when(teams.findNamesByMemberLegajo("2001")).thenReturn(List.of("Guardia mecánica"));

        assertThatThrownBy(() -> service.delete(ADMINISTRADOR, "2001")).isInstanceOfSatisfying(
                ConflictException.class, conflict -> assertThat(conflict.getMessage())
                        .contains("usuario de acceso", "Guardia mecánica"));
        verify(technicians, never()).deleteByLegajo(any());
    }

    // --- Helpers --------------------------------------------------------------------------------

    private Map<String, String> errorsOf(TechnicianCommand command) {
        try {
            service.create(ADMINISTRADOR, command);
        } catch (ValidationFailedException exception) {
            return exception.details();
        }
        throw new AssertionError("Se esperaba un ValidationFailedException");
    }

    private static void assertLegajoError(Throwable thrown) {
        assertThat(thrown).isInstanceOfSatisfying(ValidationFailedException.class, exception ->
                assertThat(exception.details()).containsOnlyKeys("legajo"));
    }

    /**
     * Un rol no permitido recibe 403 y no toca ningún puerto; uno permitido no recibe 403
     * (los stubs por defecto hacen que la operación termine bien).
     */
    private void assertAllowedOnly(Set<Role> allowed, Role role, Runnable operation) {
        if (allowed.contains(role)) {
            assertThatCode(operation::run).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(operation::run).isInstanceOf(ForbiddenOperationException.class);
            verifyNoInteractions(technicians, teams);
        }
    }
}
