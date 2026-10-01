package com.enterpriselab.api.workorders.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
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

import com.enterpriselab.api.auth.domain.AuthService;
import com.enterpriselab.api.auth.domain.ForbiddenOperationException;
import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.auth.domain.UnknownSessionUserException;
import com.enterpriselab.api.auth.domain.User;
import com.enterpriselab.api.shared.domain.ConflictException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.domain.ValidationFailedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderFlowServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00.123456Z");
    private static final Instant NOW_MILLIS = Instant.parse("2026-09-30T12:00:00.123Z");
    private static final String COMMENT = "Se reemplazó el rodamiento y se verificó el funcionamiento.";

    private static final User GUARDIA = technician(5L, "tecnico", "Técnico Mecánico de Guardia", "guardia");
    private static final User PREVENTIVO_TECH = technician(6L, "electricista", "Técnico Electricista",
            "preventivo-correctivo");

    @Mock
    private AuthService auth;
    @Mock
    private WorkOrderRepository orders;

    private WorkOrderFlowService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderFlowService(auth, orders, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // =====================================================================================================
    // take
    // =====================================================================================================

    @Test
    void takeAssignsTheUserFromTheDatabaseWithTheClockTime() {
        givenUser(GUARDIA);
        WorkOrder pending = order(7L, WorkOrderType.PRONTO_INTERVENCION, WorkOrderStatus.PENDING, null);
        when(orders.findById(7L)).thenReturn(Optional.of(pending));
        WorkOrder taken = order(7L, WorkOrderType.PRONTO_INTERVENCION, WorkOrderStatus.IN_PROGRESS,
                new TakenBy(5L, GUARDIA.displayName(), NOW_MILLIS));
        when(orders.take(anyLong(), any())).thenReturn(Optional.of(taken));

        assertThat(service.take("tecnico", "7")).isSameAs(taken);

        ArgumentCaptor<TakenBy> owner = ArgumentCaptor.forClass(TakenBy.class);
        verify(orders).take(org.mockito.ArgumentMatchers.eq(7L), owner.capture());
        assertThat(owner.getValue()).isEqualTo(new TakenBy(5L, "Técnico Mecánico de Guardia", NOW_MILLIS));
        verifyNoMoreInteractions(orders);
    }

    @Test
    void takeUsesTheRoleReadFromTheDatabase() {
        // Un usuario que en la base ya no es técnico (p. ej. team leader) no puede tomar.
        givenUser(new User(9L, "tecnico", "hash", "Ahora Team Leader", "tl@x.dev", Role.TEAM_LEADER_MANTENIMIENTO,
                null, null, null));

        assertThatThrownBy(() -> service.take("tecnico", "7")).isInstanceOf(ForbiddenOperationException.class);
        verifyNoMoreInteractions(orders);
    }

    @Test
    void aTokenOfAMissingUserIsUnauthorized() {
        when(auth.currentUser("ghost")).thenThrow(new UnknownSessionUserException("no existe"));

        assertThatThrownBy(() -> service.take("ghost", "7")).isInstanceOf(UnknownSessionUserException.class);
        assertThatThrownBy(() -> service.close("ghost", "7", validClose())).isInstanceOf(
                UnknownSessionUserException.class);
        assertThatThrownBy(() -> service.release("ghost", "7")).isInstanceOf(UnknownSessionUserException.class);
        verifyNoMoreInteractions(orders);
    }

    @Test
    void takeChecksTheRoleBeforeLookingForTheOrder() {
        givenUser(admin());

        assertThatThrownBy(() -> service.take("admin", "7")).isInstanceOf(ForbiddenOperationException.class);
        verifyNoMoreInteractions(orders);
    }

    @Test
    void takeOfAMissingOrReallyNonNumericIdIsNotFound() {
        givenUser(GUARDIA);
        when(orders.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.take("tecnico", "7")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.take("tecnico", "abc")).isInstanceOf(NotFoundException.class);
        verify(orders, never()).take(anyLong(), any());
    }

    @Test
    void takeChecksTheTeamAfterTheOrderExistsAndBeforeTheStatus() {
        givenUser(GUARDIA);
        // guardia sobre un preventivo que además ya está tomado: gana el 403 de equipo.
        when(orders.findById(7L)).thenReturn(Optional.of(order(7L, WorkOrderType.PREVENTIVO,
                WorkOrderStatus.IN_PROGRESS, new TakenBy(6L, "Otro", NOW_MILLIS))));

        assertThatThrownBy(() -> service.take("tecnico", "7")).isInstanceOf(ForbiddenOperationException.class);
        verify(orders, never()).take(anyLong(), any());
    }

    @Test
    void takeOfAnOrderThatIsNotPendingIsAConflictWithTheRealState() {
        givenUser(GUARDIA);
        TakenBy mine = new TakenBy(5L, GUARDIA.displayName(), NOW_MILLIS);
        TakenBy other = new TakenBy(6L, "Otro", NOW_MILLIS);
        for (WorkOrder order : new WorkOrder[] {
                order(7L, WorkOrderType.PRONTO_INTERVENCION, WorkOrderStatus.IN_PROGRESS, mine),
                order(7L, WorkOrderType.PRONTO_INTERVENCION, WorkOrderStatus.IN_PROGRESS, other),
                closed(7L, WorkOrderStatus.COMPLETED, other),
                closed(7L, WorkOrderStatus.CANCELLED, other)}) {
            when(orders.findById(7L)).thenReturn(Optional.of(order));

            ConflictException conflict = catchThrowableOfType(ConflictException.class,
                    () -> service.take("tecnico", "7"));

            assertThat(conflict.code()).isEqualTo("WORK_ORDER_NOT_PENDING");
            assertThat(conflict.details()).containsEntry("status", order.status().toValue())
                    .containsEntry("takenById", String.valueOf(order.takenBy().userId()))
                    .containsEntry("takenByName", order.takenBy().name());
        }
        verify(orders, never()).take(anyLong(), any());
    }

    @Test
    void takeDoesNotLookAtTheOtherOrdersOfTheTechnician() {
        givenUser(GUARDIA);
        when(orders.findById(7L)).thenReturn(Optional.of(order(7L, WorkOrderType.PRONTO_INTERVENCION,
                WorkOrderStatus.PENDING, null)));
        when(orders.take(anyLong(), any())).thenReturn(Optional.of(order(7L, WorkOrderType.PRONTO_INTERVENCION,
                WorkOrderStatus.IN_PROGRESS, new TakenBy(5L, "x", NOW_MILLIS))));

        service.take("tecnico", "7");

        verify(orders, never()).search(any(), any());
    }

    @Test
    void takeThatLosesTheRaceReportsTheRealStateOnTheRereadInsteadOfRetrying() {
        givenUser(GUARDIA);
        WorkOrder pending = order(7L, WorkOrderType.PRONTO_INTERVENCION, WorkOrderStatus.PENDING, null);
        WorkOrder taken = order(7L, WorkOrderType.PRONTO_INTERVENCION, WorkOrderStatus.IN_PROGRESS,
                new TakenBy(6L, "Otro", NOW_MILLIS));
        when(orders.findById(7L)).thenReturn(Optional.of(pending), Optional.of(taken));
        when(orders.take(anyLong(), any())).thenReturn(Optional.empty());

        ConflictException conflict = catchThrowableOfType(ConflictException.class,
                () -> service.take("tecnico", "7"));

        assertThat(conflict.code()).isEqualTo("WORK_ORDER_NOT_PENDING");
        assertThat(conflict.details()).containsEntry("status", "in-progress").containsEntry("takenById", "6");
        verify(orders, times(1)).take(anyLong(), any());
    }

    @Test
    void takeRetriesWhenTheOrderIsPendingAgain() {
        givenUser(GUARDIA);
        WorkOrder pending = order(7L, WorkOrderType.PRONTO_INTERVENCION, WorkOrderStatus.PENDING, null);
        WorkOrder taken = order(7L, WorkOrderType.PRONTO_INTERVENCION, WorkOrderStatus.IN_PROGRESS,
                new TakenBy(5L, "x", NOW_MILLIS));
        when(orders.findById(7L)).thenReturn(Optional.of(pending));
        when(orders.take(anyLong(), any())).thenReturn(Optional.empty(), Optional.of(taken));

        assertThat(service.take("tecnico", "7")).isSameAs(taken);
        verify(orders, times(2)).take(anyLong(), any());
    }

    @Test
    void takeGivesUpWithAnIllegalStateAfterThreeAttempts() {
        givenUser(GUARDIA);
        when(orders.findById(7L)).thenReturn(Optional.of(order(7L, WorkOrderType.PRONTO_INTERVENCION,
                WorkOrderStatus.PENDING, null)));
        when(orders.take(anyLong(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.take("tecnico", "7")).isInstanceOf(IllegalStateException.class);
        verify(orders, times(3)).take(anyLong(), any());
    }

    @Test
    void takeOfAnOrderDeletedInTheMiddleIsNotFound() {
        givenUser(GUARDIA);
        WorkOrder pending = order(7L, WorkOrderType.PRONTO_INTERVENCION, WorkOrderStatus.PENDING, null);
        when(orders.findById(7L)).thenReturn(Optional.of(pending), Optional.empty());
        when(orders.take(anyLong(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.take("tecnico", "7")).isInstanceOf(NotFoundException.class);
    }

    // =====================================================================================================
    // close
    // =====================================================================================================

    @Test
    void closeStoresTheTrimmedCommentWithTheUserAndTheClockTime() {
        givenUser(GUARDIA);
        TakenBy mine = new TakenBy(5L, GUARDIA.displayName(), NOW_MILLIS);
        when(orders.findById(7L)).thenReturn(Optional.of(order(7L, WorkOrderType.PRONTO_INTERVENCION,
                WorkOrderStatus.IN_PROGRESS, mine)));
        WorkOrder done = closed(7L, WorkOrderStatus.COMPLETED, mine);
        when(orders.close(anyLong(), anyLong(), any(), any())).thenReturn(Optional.of(done));

        assertThat(service.close("tecnico", "7", new CloseWorkOrderCommand("completed", "  " + COMMENT + "  ")))
                .isSameAs(done);

        ArgumentCaptor<ClosingNote> note = ArgumentCaptor.forClass(ClosingNote.class);
        verify(orders).close(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(WorkOrderStatus.COMPLETED), note.capture());
        assertThat(note.getValue()).isEqualTo(new ClosingNote(COMMENT, 5L, GUARDIA.displayName(), NOW_MILLIS));
    }

    @Test
    void closeAcceptsCancelled() {
        givenUser(GUARDIA);
        TakenBy mine = new TakenBy(5L, "x", NOW_MILLIS);
        when(orders.findById(7L)).thenReturn(Optional.of(order(7L, WorkOrderType.PRONTO_INTERVENCION,
                WorkOrderStatus.IN_PROGRESS, mine)));
        when(orders.close(anyLong(), anyLong(), any(), any()))
                .thenReturn(Optional.of(closed(7L, WorkOrderStatus.CANCELLED, mine)));

        service.close("tecnico", "7", new CloseWorkOrderCommand("cancelled", COMMENT));

        verify(orders).close(anyLong(), anyLong(), org.mockito.ArgumentMatchers.eq(WorkOrderStatus.CANCELLED),
                any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"pending", "in-progress", "Completed", " completed", "done"})
    void closeRejectsAnInvalidOutcome(String outcome) {
        givenUser(GUARDIA);

        ValidationFailedException failure = catchThrowableOfType(ValidationFailedException.class,
                () -> service.close("tecnico", "7", new CloseWorkOrderCommand(outcome, COMMENT)));

        assertThat(failure.details()).containsOnlyKeys("outcome");
        verifyNoMoreInteractions(orders);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "corto", "1234567890123456789012345678901234567890123456789"})
    void closeRejectsAMissingOrTooShortComment(String comment) {
        givenUser(GUARDIA);

        ValidationFailedException failure = catchThrowableOfType(ValidationFailedException.class,
                () -> service.close("tecnico", "7", new CloseWorkOrderCommand("completed", comment)));

        assertThat(failure.details()).containsOnlyKeys("comment");
    }

    @Test
    void closeCountsTheCommentAfterTrimming() {
        givenUser(GUARDIA);
        String paddedShort = " ".repeat(30) + "diez letra" + " ".repeat(30);

        assertThatThrownBy(() -> service.close("tecnico", "7", new CloseWorkOrderCommand("completed", paddedShort)))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void closeCommentBoundaries() {
        givenUser(GUARDIA);
        TakenBy mine = new TakenBy(5L, "x", NOW_MILLIS);
        when(orders.findById(7L)).thenReturn(Optional.of(order(7L, WorkOrderType.PRONTO_INTERVENCION,
                WorkOrderStatus.IN_PROGRESS, mine)));
        when(orders.close(anyLong(), anyLong(), any(), any()))
                .thenReturn(Optional.of(closed(7L, WorkOrderStatus.COMPLETED, mine)));

        service.close("tecnico", "7", new CloseWorkOrderCommand("completed", "a".repeat(50)));
        service.close("tecnico", "7", new CloseWorkOrderCommand("completed", "a".repeat(500)));
        assertThatThrownBy(() -> service.close("tecnico", "7",
                new CloseWorkOrderCommand("completed", "a".repeat(501))))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void closeReportsAllTheErrorsTogether() {
        givenUser(GUARDIA);

        ValidationFailedException failure = catchThrowableOfType(ValidationFailedException.class,
                () -> service.close("tecnico", "7", new CloseWorkOrderCommand(null, null)));

        assertThat(failure.details()).containsOnlyKeys("outcome", "comment");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "TECNICO", mode = EnumSource.Mode.EXCLUDE)
    void closeChecksTheRoleBeforeTheBody(Role role) {
        givenUser(nonTechnician(role));

        assertThatThrownBy(() -> service.close("x", "7", new CloseWorkOrderCommand(null, null)))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoMoreInteractions(orders);
    }

    @Test
    void closeChecksTheBodyBeforeLookingForTheOrder() {
        givenUser(GUARDIA);

        assertThatThrownBy(() -> service.close("tecnico", "999", new CloseWorkOrderCommand("x", "x")))
                .isInstanceOf(ValidationFailedException.class);
        verifyNoMoreInteractions(orders);
    }

    @Test
    void closeLooksForTheOrderBeforeCheckingTheTeam() {
        givenUser(GUARDIA);
        when(orders.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close("tecnico", "7", validClose())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.close("tecnico", "abc", validClose()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void closeChecksTheTeamBeforeTheStatus() {
        givenUser(GUARDIA);
        when(orders.findById(7L)).thenReturn(Optional.of(order(7L, WorkOrderType.CORRECTIVO,
                WorkOrderStatus.PENDING, null)));

        assertThatThrownBy(() -> service.close("tecnico", "7", validClose()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void closeOfAnOrderThatIsNotInProgressIsNotInProgressEvenIfClosedByAnotherTechnician() {
        givenUser(GUARDIA);
        TakenBy other = new TakenBy(6L, "Otro", NOW_MILLIS);
        for (WorkOrder order : new WorkOrder[] {
                order(7L, WorkOrderType.PRONTO_INTERVENCION, WorkOrderStatus.PENDING, null),
                closed(7L, WorkOrderStatus.COMPLETED, other),
                closed(7L, WorkOrderStatus.CANCELLED, new TakenBy(5L, "yo", NOW_MILLIS))}) {
            when(orders.findById(7L)).thenReturn(Optional.of(order));

            ConflictException conflict = catchThrowableOfType(ConflictException.class,
                    () -> service.close("tecnico", "7", validClose()));

            assertThat(conflict.code()).isEqualTo("WORK_ORDER_NOT_IN_PROGRESS");
            assertThat(conflict.details()).containsEntry("status", order.status().toValue());
        }
        verify(orders, never()).close(anyLong(), anyLong(), any(), any());
    }

    @Test
    void closeOfAnOrderOfAnotherTechnicianIsTakenByOtherWithTheOwner() {
        givenUser(GUARDIA);
        when(orders.findById(7L)).thenReturn(Optional.of(order(7L, WorkOrderType.PRONTO_INTERVENCION,
                WorkOrderStatus.IN_PROGRESS, new TakenBy(6L, "Otro", NOW_MILLIS))));

        ConflictException conflict = catchThrowableOfType(ConflictException.class,
                () -> service.close("tecnico", "7", validClose()));

        assertThat(conflict.code()).isEqualTo("WORK_ORDER_TAKEN_BY_OTHER");
        assertThat(conflict.details()).containsEntry("status", "in-progress").containsEntry("takenById", "6")
                .containsEntry("takenByName", "Otro");
    }

    @Test
    void closeThatLosesTheRaceReevaluatesAndRetriesWhenStillValid() {
        givenUser(GUARDIA);
        TakenBy mine = new TakenBy(5L, "x", NOW_MILLIS);
        WorkOrder inProgress = order(7L, WorkOrderType.PRONTO_INTERVENCION, WorkOrderStatus.IN_PROGRESS, mine);
        WorkOrder done = closed(7L, WorkOrderStatus.COMPLETED, mine);
        when(orders.findById(7L)).thenReturn(Optional.of(inProgress));
        when(orders.close(anyLong(), anyLong(), any(), any())).thenReturn(Optional.empty(), Optional.of(done));

        assertThat(service.close("tecnico", "7", validClose())).isSameAs(done);
    }

    @Test
    void closeThatLosesTheRaceToAReleaseReportsNotInProgress() {
        givenUser(GUARDIA);
        TakenBy mine = new TakenBy(5L, "x", NOW_MILLIS);
        when(orders.findById(7L)).thenReturn(
                Optional.of(order(7L, WorkOrderType.PRONTO_INTERVENCION, WorkOrderStatus.IN_PROGRESS, mine)),
                Optional.of(order(7L, WorkOrderType.PRONTO_INTERVENCION, WorkOrderStatus.PENDING, null)));
        when(orders.close(anyLong(), anyLong(), any(), any())).thenReturn(Optional.empty());

        ConflictException conflict = catchThrowableOfType(ConflictException.class,
                () -> service.close("tecnico", "7", validClose()));

        assertThat(conflict.code()).isEqualTo("WORK_ORDER_NOT_IN_PROGRESS");
        assertThat(conflict.details()).containsExactlyEntriesOf(Map.of("status", "pending"));
    }

    @Test
    void closeGivesUpAfterThreeAttempts() {
        givenUser(GUARDIA);
        TakenBy mine = new TakenBy(5L, "x", NOW_MILLIS);
        when(orders.findById(7L)).thenReturn(Optional.of(order(7L, WorkOrderType.PRONTO_INTERVENCION,
                WorkOrderStatus.IN_PROGRESS, mine)));
        when(orders.close(anyLong(), anyLong(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close("tecnico", "7", validClose()))
                .isInstanceOf(IllegalStateException.class);
        verify(orders, times(3)).close(anyLong(), anyLong(), any(), any());
    }

    @Test
    void closeByATechnicianOfThePreventiveTeamOnAPreventiveOrder() {
        givenUser(PREVENTIVO_TECH);
        TakenBy mine = new TakenBy(6L, "x", NOW_MILLIS);
        when(orders.findById(7L)).thenReturn(Optional.of(order(7L, WorkOrderType.PREVENTIVO,
                WorkOrderStatus.IN_PROGRESS, mine)));
        when(orders.close(anyLong(), anyLong(), any(), any()))
                .thenReturn(Optional.of(closed(7L, WorkOrderStatus.COMPLETED, mine)));

        service.close("electricista", "7", validClose());

        verify(orders).close(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(6L), any(), any());
    }

    // =====================================================================================================
    // release
    // =====================================================================================================

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMINISTRADOR", "TEAM_LEADER_MANTENIMIENTO"})
    void releaseIsAllowedToAdministratorAndTeamLeader(Role role) {
        givenUser(nonTechnician(role));
        WorkOrder inProgress = order(7L, WorkOrderType.PREVENTIVO, WorkOrderStatus.IN_PROGRESS,
                new TakenBy(5L, "x", NOW_MILLIS));
        WorkOrder pending = order(7L, WorkOrderType.PREVENTIVO, WorkOrderStatus.PENDING, null);
        when(orders.findById(7L)).thenReturn(Optional.of(inProgress));
        when(orders.release(7L)).thenReturn(Optional.of(pending));

        assertThat(service.release("x", "7")).isSameAs(pending);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"TECNICO", "PERSONAL_PRODUCCION"})
    void releaseIsForbiddenEvenOnAnOrderInTheWrongState(Role role) {
        givenUser(role == Role.TECNICO ? GUARDIA : nonTechnician(role));

        assertThatThrownBy(() -> service.release("x", "7")).isInstanceOf(ForbiddenOperationException.class);
        verifyNoMoreInteractions(orders);
    }

    @Test
    void releaseOfAMissingOrNonNumericIdIsNotFound() {
        givenUser(admin());
        when(orders.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.release("admin", "7")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.release("admin", "abc")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void releaseOfAnOrderThatIsNotInProgressIsAConflictWithTheState() {
        givenUser(admin());
        TakenBy owner = new TakenBy(5L, "Dueño", NOW_MILLIS);
        for (WorkOrder order : new WorkOrder[] {
                order(7L, WorkOrderType.PREVENTIVO, WorkOrderStatus.PENDING, null),
                closed(7L, WorkOrderStatus.COMPLETED, owner),
                closed(7L, WorkOrderStatus.CANCELLED, owner)}) {
            when(orders.findById(7L)).thenReturn(Optional.of(order));

            ConflictException conflict = catchThrowableOfType(ConflictException.class,
                    () -> service.release("admin", "7"));

            assertThat(conflict.code()).isEqualTo("WORK_ORDER_NOT_IN_PROGRESS");
            assertThat(conflict.details()).containsEntry("status", order.status().toValue());
            assertThat(conflict.details().containsKey("takenById")).isEqualTo(order.takenBy() != null);
        }
        verify(orders, never()).release(anyLong());
    }

    @Test
    void releaseThatLosesTheRaceReportsTheRealState() {
        givenUser(admin());
        WorkOrder inProgress = order(7L, WorkOrderType.PREVENTIVO, WorkOrderStatus.IN_PROGRESS,
                new TakenBy(5L, "x", NOW_MILLIS));
        WorkOrder done = closed(7L, WorkOrderStatus.COMPLETED, new TakenBy(5L, "x", NOW_MILLIS));
        when(orders.findById(7L)).thenReturn(Optional.of(inProgress), Optional.of(done));
        when(orders.release(7L)).thenReturn(Optional.empty());

        ConflictException conflict = catchThrowableOfType(ConflictException.class,
                () -> service.release("admin", "7"));

        assertThat(conflict.details()).containsEntry("status", "completed");
    }

    // =====================================================================================================
    // Ayudas
    // =====================================================================================================

    private void givenUser(User user) {
        when(auth.currentUser(any())).thenReturn(user);
    }

    private static CloseWorkOrderCommand validClose() {
        return new CloseWorkOrderCommand("completed", COMMENT);
    }

    private static User admin() {
        return nonTechnician(Role.ADMINISTRADOR);
    }

    private static User nonTechnician(Role role) {
        return new User(1L, "x", "hash", "Nombre " + role, "x@x.dev", role, null, null, null);
    }

    private static User technician(long id, String username, String displayName, String teamType) {
        return new User(id, username, "hash", displayName, username + "@x.dev", Role.TECNICO, "100" + id,
                "mecanico", teamType);
    }

    private static WorkOrder order(long id, WorkOrderType type, WorkOrderStatus status, TakenBy takenBy) {
        return new WorkOrder(id, "Orden " + id, "Descripción de prueba", new MachineRef(1L, null, "M", ""), type,
                Priority.MEDIUM, status, Instant.parse("2026-09-01T10:00:00Z"), takenBy, null);
    }

    private static WorkOrder closed(long id, WorkOrderStatus status, TakenBy owner) {
        return new WorkOrder(id, "Orden " + id, "Descripción de prueba", new MachineRef(1L, null, "M", ""),
                WorkOrderType.PRONTO_INTERVENCION, Priority.MEDIUM, status, Instant.parse("2026-09-01T10:00:00Z"),
                owner, new ClosingNote(COMMENT, owner.userId(), owner.name(), NOW_MILLIS));
    }
}
