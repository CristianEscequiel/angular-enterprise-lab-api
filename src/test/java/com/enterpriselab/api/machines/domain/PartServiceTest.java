package com.enterpriselab.api.machines.domain;

import java.util.List;
import java.util.Optional;

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
import com.enterpriselab.api.shared.domain.InvalidReferenceException;
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
class PartServiceTest {

    /** Árbol de la máquina 1: 10 → 11 (hija) y 20, que es de la máquina 2. */
    private static final Part ROOT = new Part(10L, 1L, null, "Mesa de transporte");
    private static final Part CHILD = new Part(11L, 1L, 10L, "Cinta 1");
    private static final Part OTHER_MACHINE_PART = new Part(20L, 2L, null, "Cabezal térmico");

    @Mock
    private PartRepository parts;
    @Mock
    private MachineRepository machines;

    private PartService service;

    @BeforeEach
    void setUp() {
        service = new PartService(parts, machines);
        lenient().when(machines.existsById(1L)).thenReturn(true);
        lenient().when(machines.existsById(2L)).thenReturn(true);
        lenient().when(parts.findByMachineId(1L)).thenReturn(List.of(ROOT, CHILD));
        lenient().when(parts.findById(10L)).thenReturn(Optional.of(ROOT));
        lenient().when(parts.findById(11L)).thenReturn(Optional.of(CHILD));
        lenient().when(parts.findById(20L)).thenReturn(Optional.of(OTHER_MACHINE_PART));
        lenient().when(parts.save(any(Part.class))).thenAnswer(call -> {
            Part part = call.getArgument(0);
            return new Part(part.id() == null ? 99L : part.id(), part.machineId(), part.parentId(), part.name());
        });
    }

    // --- Permisos: un test por rol y operación (REQ-31, REQ-32) --------------------------------

    @ParameterizedTest
    @EnumSource(Role.class)
    void listIsOpenToEveryRole(Role role) {
        assertThatCode(() -> service.listByMachine(role, "1")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void createIsForAdministratorAndTeamLeaderOnly(Role role) {
        assertWriteAllowedOnlyForManagers(role, () -> service.create(role, "1", new PartCommand("Nueva", null)));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void renameIsForAdministratorAndTeamLeaderOnly(Role role) {
        assertWriteAllowedOnlyForManagers(role, () -> service.rename(role, "10", patch("Otro")));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void deleteIsForAdministratorAndTeamLeaderOnly(Role role) {
        assertWriteAllowedOnlyForManagers(role, () -> service.delete(role, "11"));
    }

    @Test
    void aForbiddenRoleNeverReachesTheRepositories() {
        assertThatThrownBy(() -> service.create(Role.TECNICO, "abc", new PartCommand("", "zzz")))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.rename(Role.PERSONAL_PRODUCCION, "abc", patch(null)))
                .isInstanceOf(ForbiddenOperationException.class);

        verifyNoInteractions(parts, machines);
    }

    // --- Lectura (REQ-16, REQ-17) --------------------------------------------------------------

    @Test
    void listReturnsTheFlatListOfTheMachine() {
        assertThat(service.listByMachine(Role.PERSONAL_PRODUCCION, "1")).containsExactly(ROOT, CHILD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "abc", "-1"})
    void listOfAnUnknownOrNonNumericMachineIsNotFound(String machineId) {
        assertThatThrownBy(() -> service.listByMachine(ADMINISTRADOR, machineId))
                .isInstanceOf(NotFoundException.class);
    }

    // --- Alta (REQ-18 a REQ-23) ----------------------------------------------------------------

    @Test
    void createAtTheTopLevelSavesAPartWithoutParent() {
        Part created = service.create(ADMINISTRADOR, "1", new PartCommand("  Nueva  ", null));

        ArgumentCaptor<Part> saved = ArgumentCaptor.forClass(Part.class);
        verify(parts).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(new Part(null, 1L, null, "Nueva"));
        assertThat(created).isEqualTo(new Part(99L, 1L, null, "Nueva"));
    }

    @Test
    void createASubPartSavesTheParentOfTheSameMachine() {
        Part created = service.create(TEAM_LEADER_MANTENIMIENTO, "1", new PartCommand("Motor", "11"));

        assertThat(created).isEqualTo(new Part(99L, 1L, 11L, "Motor"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "abc", "", " ", "-1", "10.0"})
    void createWithAParentThatIsNotAPartIsParentPartNotFound(String parentId) {
        assertThat(catchThrowable(() -> service.create(ADMINISTRADOR, "1", new PartCommand("Nueva", parentId))))
                .isInstanceOfSatisfying(InvalidReferenceException.class,
                        failure -> assertThat(failure.code()).isEqualTo("PARENT_PART_NOT_FOUND"));
        verify(parts, never()).save(any());
    }

    @Test
    void createWithAParentOfAnotherMachineIsParentPartOtherMachine() {
        assertThat(catchThrowable(() -> service.create(ADMINISTRADOR, "1", new PartCommand("Nueva", "20"))))
                .isInstanceOfSatisfying(InvalidReferenceException.class,
                        failure -> assertThat(failure.code()).isEqualTo("PARENT_PART_OTHER_MACHINE"));
        verify(parts, never()).save(any());
    }

    @Test
    void createInAnUnknownMachineIsNotFoundEvenWithABadParent() {
        // REQ-36: el 404 de la máquina gana al 400 de referencia del padre.
        assertThatThrownBy(() -> service.create(ADMINISTRADOR, "999", new PartCommand("Nueva", "zzz")))
                .isInstanceOf(NotFoundException.class);
        verify(parts, never()).save(any());
    }

    @Test
    void anInvalidNameIs400BeforeTheMachineNotFound() {
        // REQ-36: el 400 de formato gana al 404.
        assertThat(catchThrowable(() -> service.create(ADMINISTRADOR, "999", new PartCommand("  ", "zzz"))))
                .isInstanceOf(ValidationFailedException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void aMissingOrBlankNameIsAValidationErrorOnCreateAndRename(String name) {
        assertThat(catchThrowable(() -> service.create(ADMINISTRADOR, "1", new PartCommand(name, null))))
                .isInstanceOfSatisfying(ValidationFailedException.class,
                        failure -> assertThat(failure.details()).containsOnlyKeys("name"));
        assertThat(catchThrowable(() -> service.rename(ADMINISTRADOR, "10", patch(name))))
                .isInstanceOfSatisfying(ValidationFailedException.class,
                        failure -> assertThat(failure.details()).containsOnlyKeys("name"));
        verify(parts, never()).save(any());
    }

    @Test
    void theNameCanHave100CharactersButNot101AfterTrimming() {
        assertThatCode(() -> service.create(ADMINISTRADOR, "1", new PartCommand(" " + "n".repeat(100) + " ", null)))
                .doesNotThrowAnyException();
        assertThat(catchThrowable(() -> service.create(ADMINISTRADOR, "1", new PartCommand("n".repeat(101), null))))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(catchThrowable(() -> service.rename(ADMINISTRADOR, "10", patch("n".repeat(101)))))
                .isInstanceOf(ValidationFailedException.class);
    }

    // --- Edición del nombre (REQ-24, REQ-25, REQ-26) -------------------------------------------

    @Test
    void renameChangesOnlyTheNameAndKeepsMachineAndParent() {
        Part renamed = service.rename(ADMINISTRADOR, "11", patch("  Cinta principal "));

        assertThat(renamed).isEqualTo(new Part(11L, 1L, 10L, "Cinta principal"));
    }

    @Test
    void renameAcceptsTheCurrentMachineAndParentIdsWhenSent() {
        Part renamed = service.rename(ADMINISTRADOR, "11", new PartPatchCommand("X", true, "1", true, "10"));

        assertThat(renamed).isEqualTo(new Part(11L, 1L, 10L, "X"));
    }

    @Test
    void renameOfATopLevelPartAcceptsAnExplicitNullParent() {
        Part renamed = service.rename(ADMINISTRADOR, "10", new PartPatchCommand("X", false, null, true, null));

        assertThat(renamed).isEqualTo(new Part(10L, 1L, null, "X"));
    }

    @Test
    void renameWithADifferentMachineIsAValidationErrorOnMachineId() {
        assertThat(catchThrowable(() ->
                service.rename(ADMINISTRADOR, "11", new PartPatchCommand("X", true, "2", false, null))))
                .isInstanceOfSatisfying(ValidationFailedException.class,
                        failure -> assertThat(failure.details()).containsOnlyKeys("machineId"));
        verify(parts, never()).save(any());
    }

    @Test
    void renameWithADifferentParentIsAValidationErrorOnParentId() {
        assertThat(catchThrowable(() ->
                service.rename(ADMINISTRADOR, "11", new PartPatchCommand("X", false, null, true, "20"))))
                .isInstanceOfSatisfying(ValidationFailedException.class,
                        failure -> assertThat(failure.details()).containsOnlyKeys("parentId"));
        verify(parts, never()).save(any());
    }

    @Test
    void anExplicitNullParentOnASubPartIsAnAttemptToMoveItToTheTopLevel() {
        assertThat(catchThrowable(() ->
                service.rename(ADMINISTRADOR, "11", new PartPatchCommand("X", false, null, true, null))))
                .isInstanceOfSatisfying(ValidationFailedException.class,
                        failure -> assertThat(failure.details()).containsOnlyKeys("parentId"));
        verify(parts, never()).save(any());
    }

    @Test
    void renameOfAnUnknownPartIsNotFoundBeforeTheMoveCheck() {
        assertThatThrownBy(() ->
                service.rename(ADMINISTRADOR, "999", new PartPatchCommand("X", true, "2", true, "20")))
                .isInstanceOf(NotFoundException.class);
    }

    // --- Baja (REQ-27, REQ-28, REQ-29) ---------------------------------------------------------

    @Test
    void deleteRemovesALeaf() {
        when(parts.countChildren(11L)).thenReturn(0);

        service.delete(ADMINISTRADOR, "11");

        verify(parts).deleteById(11L);
    }

    @Test
    void deleteOfAPartWithChildrenIsAConflictWithTheCountInTheMessage() {
        when(parts.countChildren(10L)).thenReturn(2);

        assertThat(catchThrowable(() -> service.delete(ADMINISTRADOR, "10")))
                .isInstanceOfSatisfying(ConflictException.class, conflict -> {
                    assertThat(conflict.code()).isEqualTo("PART_HAS_CHILDREN");
                    assertThat(conflict.getMessage()).contains("Mesa de transporte").contains("2 sub-partes");
                });
        verify(parts, never()).deleteById(anyLong());
    }

    @Test
    void deleteOfAnUnknownOrNonNumericPartIsNotFound() {
        assertThatThrownBy(() -> service.delete(ADMINISTRADOR, "999")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(ADMINISTRADOR, "abc")).isInstanceOf(NotFoundException.class);
        verify(parts, never()).deleteById(anyLong());
    }

    // --- Ayudas --------------------------------------------------------------------------------

    private static PartPatchCommand patch(String name) {
        return new PartPatchCommand(name, false, null, false, null);
    }

    private static void assertWriteAllowedOnlyForManagers(Role role, Runnable operation) {
        if (role == ADMINISTRADOR || role == TEAM_LEADER_MANTENIMIENTO) {
            assertThatCode(operation::run).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(operation::run).isInstanceOf(ForbiddenOperationException.class);
        }
    }
}
