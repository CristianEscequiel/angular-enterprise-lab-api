package com.enterpriselab.api.machines.domain;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineServiceTest {

    private static final Machine EXISTING = new Machine(1L, "ENV-01", "Envasadora línea 1", 7);

    @Mock
    private MachineRepository machines;

    private MachineService service;

    @BeforeEach
    void setUp() {
        service = new MachineService(machines);
        // Caminos felices por defecto: la máquina 1 existe y ningún código está usado.
        lenient().when(machines.findAll()).thenReturn(List.of(EXISTING));
        lenient().when(machines.findById(1L)).thenReturn(Optional.of(EXISTING));
        lenient().when(machines.save(any(Machine.class))).thenAnswer(call -> {
            Machine machine = call.getArgument(0);
            return new Machine(machine.id() == null ? 4L : machine.id(), machine.code(), machine.name(),
                    machine.partCount());
        });
    }

    // --- Permisos: un test por rol y operación (REQ-31, REQ-32) --------------------------------

    @ParameterizedTest
    @EnumSource(Role.class)
    void listIsOpenToEveryRole(Role role) {
        assertThatCode(() -> service.list(role)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void getIsOpenToEveryRole(Role role) {
        assertThatCode(() -> service.get(role, "1")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void createIsForAdministratorAndTeamLeaderOnly(Role role) {
        assertWriteAllowedOnlyForManagers(role, () -> service.create(role, new MachineCommand("NEW-01", "Nueva")));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void updateIsForAdministratorAndTeamLeaderOnly(Role role) {
        assertWriteAllowedOnlyForManagers(role, () -> service.update(role, "1", new MachineCommand("ENV-01", "X")));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void deleteIsForAdministratorAndTeamLeaderOnly(Role role) {
        Machine withoutParts = new Machine(1L, "ENV-01", "Envasadora", 0);
        lenient().when(machines.findById(1L)).thenReturn(Optional.of(withoutParts));

        assertWriteAllowedOnlyForManagers(role, () -> service.delete(role, "1"));
    }

    @Test
    void aForbiddenRoleNeverReachesTheRepository() {
        assertThatThrownBy(() -> service.create(Role.TECNICO, new MachineCommand("", null)))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.update(Role.PERSONAL_PRODUCCION, "abc", new MachineCommand("", null)))
                .isInstanceOf(ForbiddenOperationException.class);

        verifyNoInteractions(machines);
    }

    // --- Lectura (REQ-1, REQ-2, REQ-3) ---------------------------------------------------------

    @Test
    void listAndGetReturnTheMachinesWithTheirPartCount() {
        assertThat(service.list(ADMINISTRADOR)).containsExactly(EXISTING);
        assertThat(service.get(Role.TECNICO, "1")).isEqualTo(EXISTING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "abc", "1.5", "-1", "0x1", "１", "1234567890123456789"})
    void getOfAnUnknownOrNonNumericIdIsNotFound(String id) {
        assertThatThrownBy(() -> service.get(ADMINISTRADOR, id)).isInstanceOf(NotFoundException.class);
    }

    // --- Alta (REQ-4, REQ-5, REQ-6, REQ-7, REQ-8) ----------------------------------------------

    @Test
    void createSavesTheNormalizedCodeAndTheTrimmedNameWithZeroParts() {
        Machine created = service.create(TEAM_LEADER_MANTENIMIENTO, new MachineCommand("  new-01 ", "  Nueva  "));

        ArgumentCaptor<Machine> saved = ArgumentCaptor.forClass(Machine.class);
        verify(machines).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(new Machine(null, "NEW-01", "Nueva", 0));
        assertThat(created).isEqualTo(new Machine(4L, "NEW-01", "Nueva", 0));
        verify(machines).existsByCode("NEW-01");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "-A", "A B", "A_1", "AAAAAAAAAAAAAAAAAAAAA"})
    void createWithAnInvalidCodeIsAValidationErrorOnCode(String code) {
        assertValidation(() -> service.create(ADMINISTRADOR, new MachineCommand(code, "Nueva")), "code");
        verify(machines, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void createWithAMissingOrBlankNameIsAValidationErrorOnName(String name) {
        assertValidation(() -> service.create(ADMINISTRADOR, new MachineCommand("NEW-01", name)), "name");
        verify(machines, never()).save(any());
    }

    @Test
    void theNameCanHave100CharactersButNot101AfterTrimming() {
        assertThatCode(() -> service.create(ADMINISTRADOR, new MachineCommand("NEW-01", " " + "n".repeat(100) + " ")))
                .doesNotThrowAnyException();
        assertValidation(() -> service.create(ADMINISTRADOR, new MachineCommand("NEW-01", "n".repeat(101))), "name");
    }

    @Test
    void allFieldErrorsAreReportedTogether() {
        ValidationFailedException failure = (ValidationFailedException) catchThrowable(() ->
                service.create(ADMINISTRADOR, new MachineCommand("!!", null)));

        assertThat(failure.details()).containsOnlyKeys("code", "name");
    }

    @Test
    void createWithAUsedCodeIsADuplicateConflictAndSavesNothing() {
        when(machines.existsByCode("ENV-01")).thenReturn(true);

        assertThat(catchThrowable(() -> service.create(ADMINISTRADOR, new MachineCommand("env-01", "Otra"))))
                .isInstanceOfSatisfying(ConflictException.class, conflict -> {
                    assertThat(conflict.code()).isEqualTo("DUPLICATE_MACHINE_CODE");
                    assertThat(conflict.getMessage()).contains("ENV-01");
                });
        verify(machines, never()).save(any());
    }

    // --- Edición (REQ-9, REQ-10, REQ-11, REQ-12) -----------------------------------------------

    @Test
    void updateChangesCodeAndNameAndKeepsIdAndPartCount() {
        Machine updated = service.update(ADMINISTRADOR, "1", new MachineCommand(" env-99 ", " Nueva "));

        assertThat(updated).isEqualTo(new Machine(1L, "ENV-99", "Nueva", 7));
        verify(machines).existsByCodeAndIdNot("ENV-99", 1L);
    }

    @Test
    void aMachineIsNotADuplicateOfItself() {
        // La edición consulta "otra máquina con ese código" (excluye a la propia) y no "cualquiera".
        when(machines.existsByCodeAndIdNot("ENV-01", 1L)).thenReturn(false);

        assertThatCode(() -> service.update(ADMINISTRADOR, "1", new MachineCommand("env-01", "Solo cambia el nombre")))
                .doesNotThrowAnyException();
        verify(machines, never()).existsByCode(any());
    }

    @Test
    void updateWithTheCodeOfAnotherMachineIsADuplicateConflictAndSavesNothing() {
        when(machines.existsByCodeAndIdNot("SEL-02", 1L)).thenReturn(true);

        assertThat(catchThrowable(() -> service.update(ADMINISTRADOR, "1", new MachineCommand("sel-02", "X"))))
                .isInstanceOfSatisfying(ConflictException.class,
                        conflict -> assertThat(conflict.code()).isEqualTo("DUPLICATE_MACHINE_CODE"));
        verify(machines, never()).save(any());
    }

    @Test
    void updateOfAnUnknownMachineIsNotFoundAndCreatesNothing() {
        assertThatThrownBy(() -> service.update(ADMINISTRADOR, "999", new MachineCommand("NEW-01", "X")))
                .isInstanceOf(NotFoundException.class);
        verify(machines, never()).save(any());
    }

    @Test
    void anInvalidBodyOnAnUnknownMachineIs400BeforeTheNotFound() {
        assertValidation(() -> service.update(ADMINISTRADOR, "999", new MachineCommand("", "")), "code", "name");
    }

    // --- Baja (REQ-13, REQ-14, REQ-15) ---------------------------------------------------------

    @Test
    void deleteRemovesAMachineWithoutParts() {
        when(machines.countParts(1L)).thenReturn(0);

        service.delete(ADMINISTRADOR, "1");

        verify(machines).deleteById(1L);
    }

    @Test
    void deleteOfAMachineWithPartsIsAConflictWithTheCountInTheMessage() {
        when(machines.countParts(1L)).thenReturn(7);

        assertThat(catchThrowable(() -> service.delete(ADMINISTRADOR, "1")))
                .isInstanceOfSatisfying(ConflictException.class, conflict -> {
                    assertThat(conflict.code()).isEqualTo("MACHINE_HAS_PARTS");
                    assertThat(conflict.getMessage()).contains("ENV-01").contains("7 partes");
                });
        verify(machines, never()).deleteById(anyLong());
    }

    @Test
    void theMessageUsesTheSingularForOnePart() {
        when(machines.countParts(1L)).thenReturn(1);

        assertThat(catchThrowable(() -> service.delete(ADMINISTRADOR, "1"))).hasMessageContaining("1 parte")
                .hasMessageNotContaining("1 partes");
    }

    @Test
    void deleteOfAnUnknownMachineIsNotFound() {
        assertThatThrownBy(() -> service.delete(ADMINISTRADOR, "999")).isInstanceOf(NotFoundException.class);
        verify(machines, never()).deleteById(anyLong());
    }

    // --- Ayudas --------------------------------------------------------------------------------

    private static void assertWriteAllowedOnlyForManagers(Role role, Runnable operation) {
        if (role == ADMINISTRADOR || role == TEAM_LEADER_MANTENIMIENTO) {
            assertThatCode(operation::run).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(operation::run).isInstanceOf(ForbiddenOperationException.class);
        }
    }

    private static void assertValidation(Runnable operation, String... keys) {
        Consumer<Throwable> check = failure -> {
            assertThat(failure).isInstanceOf(ValidationFailedException.class);
            assertThat(((ValidationFailedException) failure).details()).containsOnlyKeys(keys);
        };
        check.accept(catchThrowable(operation::run));
    }
}
