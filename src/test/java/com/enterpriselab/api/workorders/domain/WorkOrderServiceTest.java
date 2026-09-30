package com.enterpriselab.api.workorders.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

import com.enterpriselab.api.auth.domain.ForbiddenOperationException;
import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.shared.domain.InvalidReferenceException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.domain.PageQuery;
import com.enterpriselab.api.shared.domain.PageResult;
import com.enterpriselab.api.shared.domain.ValidationFailedException;

import static com.enterpriselab.api.auth.domain.Role.ADMINISTRADOR;
import static com.enterpriselab.api.auth.domain.Role.PERSONAL_PRODUCCION;
import static com.enterpriselab.api.auth.domain.Role.TECNICO;
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

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00.123456Z");
    private static final Instant CREATED = Instant.parse("2026-08-03T14:00:00Z");

    @Mock
    private WorkOrderRepository orders;
    @Mock
    private MachineDirectory directory;

    private WorkOrderService service;

    @BeforeEach
    void setUp() {
        service = new WorkOrderService(orders, directory, Clock.fixed(NOW, ZoneOffset.UTC));
        // Caminos felices por defecto: máquinas 1 y 2, partes 3 (nivel 3 de la máquina 1), 50 (nivel 5) y 9 (máquina 2).
        lenient().when(directory.findMachineName(1L)).thenReturn(Optional.of("Envasadora línea 1"));
        lenient().when(directory.findMachineName(2L)).thenReturn(Optional.of("Selladora"));
        lenient().when(directory.locatePart(3L)).thenReturn(Optional.of(new PartLocation(1L,
                List.of("Mesa de transporte", "Cinta 1", "Motor de cinta"))));
        lenient().when(directory.locatePart(50L)).thenReturn(Optional.of(new PartLocation(1L,
                List.of("N1", "N2", "N3", "N4", "N5"))));
        lenient().when(directory.locatePart(9L)).thenReturn(Optional.of(new PartLocation(2L,
                List.of("Cabezal térmico", "Resistencia"))));
        // Orden 1: en progreso, sobre la parte 3, con dueño. Orden 2: pendiente, sobre la máquina completa.
        lenient().when(orders.findById(1L)).thenReturn(Optional.of(existing(1L, WorkOrderStatus.IN_PROGRESS, 3L)));
        lenient().when(orders.findById(2L)).thenReturn(Optional.of(existing(2L, WorkOrderStatus.PENDING, null)));
        lenient().when(orders.save(any(WorkOrder.class))).thenAnswer(call -> {
            WorkOrder order = call.getArgument(0);
            return new WorkOrder(order.id() == null ? 99L : order.id(), order.title(), order.description(),
                    order.machineRef(), order.type(), order.priority(), order.status(), order.createdAt(),
                    order.takenBy(), order.closingNote());
        });
    }

    // =====================================================================================================
    // Listado y consulta (REQ-1, REQ-3, REQ-5, REQ-8, REQ-12, REQ-13, REQ-14, REQ-42)
    // =====================================================================================================

    @Test
    void listWithoutParametersAsksForThePageOneOfTenWithoutFilters() {
        PageResult<WorkOrder> empty = new PageResult<>(List.of(), 1, 10, 0);
        lenient().when(orders.search(any(), any())).thenReturn(empty);

        PageResult<WorkOrder> result = service.list(TECNICO, query(null, null, null, null, null));

        ArgumentCaptor<WorkOrderFilter> filter = ArgumentCaptor.forClass(WorkOrderFilter.class);
        ArgumentCaptor<PageQuery> page = ArgumentCaptor.forClass(PageQuery.class);
        verify(orders).search(filter.capture(), page.capture());
        assertThat(page.getValue()).isEqualTo(new PageQuery(1, 10));
        assertThat(filter.getValue()).isEqualTo(new WorkOrderFilter(null, null, null));
        assertThat(result).isSameAs(empty);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleCanListAndGet(Role role) {
        assertThatCode(() -> service.list(role, query(null, null, null, null, null))).doesNotThrowAnyException();
        assertThatCode(() -> service.get(role, "1")).doesNotThrowAnyException();
    }

    @Test
    void listPassesPageSizeAndTheThreeFilters() {
        service.list(ADMINISTRADOR, query("3", "25", "  Motor  ", "in-progress", "high"));

        ArgumentCaptor<WorkOrderFilter> filter = ArgumentCaptor.forClass(WorkOrderFilter.class);
        ArgumentCaptor<PageQuery> page = ArgumentCaptor.forClass(PageQuery.class);
        verify(orders).search(filter.capture(), page.capture());
        assertThat(page.getValue()).isEqualTo(new PageQuery(3, 25));
        assertThat(filter.getValue()).isEqualTo(
                new WorkOrderFilter("Motor", WorkOrderStatus.IN_PROGRESS, Priority.HIGH));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2", "999999"})
    void validPages(String page) {
        assertThatCode(() -> service.list(ADMINISTRADOR, query(page, null, null, null, null)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "50", "100"})
    void validSizes(String size) {
        assertThatCode(() -> service.list(ADMINISTRADOR, query(null, size, null, null, null)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "", " ", "１", "99999999999", "2147483648", "+1"})
    void invalidPagesAreAValidationErrorOnPage(String page) {
        assertValidation(() -> service.list(ADMINISTRADOR, query(page, null, null, null, null)), "page");
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "101", "abc", "1.5", "", "99999999999"})
    void invalidSizesAreAValidationErrorOnSize(String size) {
        assertValidation(() -> service.list(ADMINISTRADOR, query(null, size, null, null, null)), "size");
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pending", "open", "in_progress", " pending", "PENDING"})
    void anInvalidStatusIsAValidationErrorAndNotAnEmptyList(String status) {
        assertValidation(() -> service.list(ADMINISTRADOR, query(null, null, null, status, null)), "status");
    }

    @ParameterizedTest
    @ValueSource(strings = {"High", "urgent", " low", "MEDIUM"})
    void anInvalidPriorityIsAValidationErrorAndNotAnEmptyList(String priority) {
        assertValidation(() -> service.list(ADMINISTRADOR, query(null, null, null, null, priority)), "priority");
    }

    @Test
    void everyValidStatusAndPriorityIsAccepted() {
        for (WorkOrderStatus status : WorkOrderStatus.values()) {
            assertThatCode(() -> service.list(ADMINISTRADOR, query(null, null, null, status.toValue(), null)))
                    .doesNotThrowAnyException();
        }
        for (Priority priority : Priority.values()) {
            assertThatCode(() -> service.list(ADMINISTRADOR, query(null, null, null, null, priority.toValue())))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void allParameterErrorsAreReportedTogether() {
        assertValidation(() -> service.list(ADMINISTRADOR, query("0", "0", null, "x", "y")),
                "page", "size", "status", "priority");
    }

    @Test
    void anEmptyStatusOrPriorityDoesNotFilter() {
        service.list(ADMINISTRADOR, query(null, null, null, "", ""));

        ArgumentCaptor<WorkOrderFilter> filter = ArgumentCaptor.forClass(WorkOrderFilter.class);
        verify(orders).search(filter.capture(), any());
        assertThat(filter.getValue()).isEqualTo(new WorkOrderFilter(null, null, null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void aBlankTitleDoesNotFilter(String title) {
        service.list(ADMINISTRADOR, query(null, null, title, null, null));

        ArgumentCaptor<WorkOrderFilter> filter = ArgumentCaptor.forClass(WorkOrderFilter.class);
        verify(orders).search(filter.capture(), any());
        assertThat(filter.getValue().titleText()).isNull();
    }

    @Test
    void getReturnsTheOrder() {
        assertThat(service.get(ADMINISTRADOR, "1").id()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "abc", "-1", "1.5", "１", "1234567890123456789"})
    void getOfAnUnknownOrNonNumericIdIsNotFound(String id) {
        assertThatThrownBy(() -> service.get(ADMINISTRADOR, id)).isInstanceOf(NotFoundException.class);
    }

    // =====================================================================================================
    // Alta (REQ-15 a REQ-31, REQ-46)
    // =====================================================================================================

    @Test
    void createBuildsAPendingOrderWithTheServerDecidedFields() {
        WorkOrder created = service.create(TEAM_LEADER_MANTENIMIENTO, validCreate("preventivo", "1", null));

        ArgumentCaptor<WorkOrder> saved = ArgumentCaptor.forClass(WorkOrder.class);
        verify(orders).save(saved.capture());
        WorkOrder order = saved.getValue();
        assertThat(order.id()).isNull();
        assertThat(order.status()).isEqualTo(WorkOrderStatus.PENDING);
        assertThat(order.takenBy()).isNull();
        assertThat(order.closingNote()).isNull();
        assertThat(order.createdAt()).isEqualTo(Instant.parse("2026-09-30T12:00:00.123Z"));
        assertThat(order.title()).isEqualTo("Revisar motor");
        assertThat(order.type()).isEqualTo(WorkOrderType.PREVENTIVO);
        assertThat(order.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(created.id()).isEqualTo(99L);
    }

    @Test
    void eachRoleCreatesItsOwnTypes() {
        assertThatCode(() -> service.create(TEAM_LEADER_MANTENIMIENTO, validCreate("preventivo", "1", null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.create(TEAM_LEADER_MANTENIMIENTO, validCreate("correctivo", "1", null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.create(PERSONAL_PRODUCCION, validCreate("pronto-intervencion", "1", null)))
                .doesNotThrowAnyException();
    }

    /** La matriz completa: 4 roles × 3 tipos, con el 403 y nada consultado ni guardado. */
    @ParameterizedTest
    @EnumSource(Role.class)
    void createRoleTypeMatrix(Role role) {
        for (WorkOrderType type : WorkOrderType.values()) {
            boolean allowed = WorkOrderPermissions.canCreate(role, type);
            if (allowed) {
                assertThatCode(() -> service.create(role, validCreate(type.toValue(), "1", null)))
                        .as("%s crea %s", role, type).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> service.create(role, validCreate(type.toValue(), "1", null)))
                        .as("%s crea %s", role, type).isInstanceOf(ForbiddenOperationException.class);
            }
        }
    }

    @Test
    void aForbiddenCreateNeverReachesTheRepositoriesEvenWithAnInvalidBody() {
        CreateWorkOrderCommand empty = new CreateWorkOrderCommand(null, null, null, null, false, null, null, null);

        assertThatThrownBy(() -> service.create(ADMINISTRADOR, empty)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.create(TECNICO, empty)).isInstanceOf(ForbiddenOperationException.class);
        // Tipo válido que el rol no puede crear + otros campos inválidos: 403 gana a 400.
        CreateWorkOrderCommand badFieldsButValidType = new CreateWorkOrderCommand("x", "y", "pronto-intervencion",
                "nope", false, null, null, null);
        assertThatThrownBy(() -> service.create(TEAM_LEADER_MANTENIMIENTO, badFieldsButValidType))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.create(PERSONAL_PRODUCCION,
                new CreateWorkOrderCommand("x", "y", "preventivo", "nope", false, null, null, null)))
                .isInstanceOf(ForbiddenOperationException.class);

        verifyNoInteractions(orders, directory);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Preventivo", "otro", "", "pronto_intervencion", " preventivo"})
    void anInvalidTypeIsAValidationErrorAndNotAForbidden(String type) {
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, validCreate(type, "1", null)), "type");
        assertValidation(() -> service.create(PERSONAL_PRODUCCION, validCreate(type, "1", null)), "type");
    }

    @Test
    void aMissingTypeIsAValidationError() {
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, validCreate(null, "1", null)), "type");
    }

    @Test
    void theTitleHasBetween3And150CharactersAfterTrimming() {
        assertThatCode(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withTitle("abc"))).doesNotThrowAnyException();
        assertThatCode(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withTitle(" " + "t".repeat(150) + " ")))
                .doesNotThrowAnyException();
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withTitle("ab")), "title");
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withTitle("  ab  ")), "title");
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withTitle("t".repeat(151))), "title");
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withTitle(null)), "title");
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withTitle("   ")), "title");
    }

    @Test
    void theDescriptionHasBetween10And2000CharactersAfterTrimming() {
        assertThatCode(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withDescription("d".repeat(10))))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withDescription(" " + "d".repeat(2000) + " ")))
                .doesNotThrowAnyException();
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withDescription("d".repeat(9))), "description");
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withDescription("  " + "d".repeat(9) + " ")),
                "description");
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withDescription("d".repeat(2001))),
                "description");
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withDescription(null)), "description");
    }

    @Test
    void titleAndDescriptionAreSavedTrimmed() {
        service.create(TEAM_LEADER_MANTENIMIENTO, new CreateWorkOrderCommand("  Título  ", "  Descripción larga  ",
                "correctivo", "low", true, "1", null, null));

        ArgumentCaptor<WorkOrder> saved = ArgumentCaptor.forClass(WorkOrder.class);
        verify(orders).save(saved.capture());
        assertThat(saved.getValue().title()).isEqualTo("Título");
        assertThat(saved.getValue().description()).isEqualTo("Descripción larga");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Low", "urgent", "", " high", "HIGH"})
    void anInvalidPriorityIsAValidationError(String priority) {
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, new CreateWorkOrderCommand("Título",
                "Descripción larga", "correctivo", priority, true, "1", null, null)), "priority");
    }

    @Test
    void aMissingPriorityIsAValidationError() {
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, new CreateWorkOrderCommand("Título",
                "Descripción larga", "correctivo", null, true, "1", null, null)), "priority");
    }

    @Test
    void allFieldErrorsAreReportedTogether() {
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, new CreateWorkOrderCommand("a", "b", "otro",
                "nope", false, null, null, null)), "title", "description", "type", "priority", "machineRef");
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, new CreateWorkOrderCommand("a", "b", "otro",
                "nope", true, null, null, "c".repeat(201))),
                "title", "description", "type", "priority", "machineRef.machineId", "machineRef.comment");
    }

    @Test
    void aMissingMachineRefOrMachineIdIsAValidationError() {
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, new CreateWorkOrderCommand("Título",
                "Descripción larga", "correctivo", "low", false, null, null, null)), "machineRef");
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, new CreateWorkOrderCommand("Título",
                "Descripción larga", "correctivo", "low", true, null, "3", null)), "machineRef.machineId");
        verify(orders, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "", "abc", " ", "-1"})
    void aMachineThatDoesNotResolveIsMachineNotFound(String machineId) {
        assertReference(() -> service.create(TEAM_LEADER_MANTENIMIENTO, validCreate("correctivo", machineId, null)),
                "MACHINE_NOT_FOUND");
        verify(orders, never()).save(any());
    }

    @Test
    void anOrderOnTheWholeMachineHasTheMachineNameAsBreadcrumb() {
        service.create(TEAM_LEADER_MANTENIMIENTO, validCreate("correctivo", "2", null));

        WorkOrder order = savedOrder();
        assertThat(order.machineRef()).isEqualTo(new MachineRef(2L, null, "Selladora", ""));
    }

    @Test
    void anOrderOnAPartHasTheWholeChainSeparatedByTheArrow() {
        service.create(TEAM_LEADER_MANTENIMIENTO, validCreate("correctivo", "1", "3"));

        assertThat(savedOrder().machineRef()).isEqualTo(new MachineRef(1L, 3L,
                "Envasadora línea 1 > Mesa de transporte > Cinta 1 > Motor de cinta", ""));
    }

    @Test
    void aFifthLevelPartKeepsEveryLevelOfTheChain() {
        service.create(TEAM_LEADER_MANTENIMIENTO, validCreate("correctivo", "1", "50"));

        assertThat(savedOrder().machineRef().breadcrumb())
                .isEqualTo("Envasadora línea 1 > N1 > N2 > N3 > N4 > N5");
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "", "abc", " ", "-1"})
    void aPartThatDoesNotResolveIsPartNotFound(String partId) {
        assertReference(() -> service.create(TEAM_LEADER_MANTENIMIENTO, validCreate("correctivo", "1", partId)),
                "PART_NOT_FOUND");
        verify(orders, never()).save(any());
    }

    @Test
    void aPartOfAnotherMachineIsPartOtherMachine() {
        assertReference(() -> service.create(TEAM_LEADER_MANTENIMIENTO, validCreate("correctivo", "1", "9")),
                "PART_OTHER_MACHINE");
        verify(orders, never()).save(any());
    }

    @Test
    void whenBothReferencesAreBadTheMachineWins() {
        assertReference(() -> service.create(TEAM_LEADER_MANTENIMIENTO, validCreate("correctivo", "999", "888")),
                "MACHINE_NOT_FOUND");
    }

    @Test
    void aFormatErrorWinsOverAnUnresolvedReferenceWithoutAskingForIt() {
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, new CreateWorkOrderCommand("a",
                "Descripción larga", "correctivo", "low", true, "999", "888", null)), "title");
        verifyNoInteractions(directory);
    }

    @Test
    void theCommentIsSavedTrimmedAndOutsideTheBreadcrumb() {
        service.create(TEAM_LEADER_MANTENIMIENTO, new CreateWorkOrderCommand("Título", "Descripción larga",
                "correctivo", "low", true, "1", "3", "  Hace ruido  "));

        MachineRef ref = savedOrder().machineRef();
        assertThat(ref.comment()).isEqualTo("Hace ruido");
        assertThat(ref.breadcrumb()).doesNotContain("Hace ruido");
    }

    @Test
    void aMissingCommentIsSavedAsAnEmptyString() {
        service.create(TEAM_LEADER_MANTENIMIENTO, new CreateWorkOrderCommand("Título", "Descripción larga",
                "correctivo", "low", true, "1", null, null));

        assertThat(savedOrder().machineRef().comment()).isEmpty();
    }

    @Test
    void theCommentHasAtMost200Characters() {
        assertThatCode(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withComment("c".repeat(200))))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withComment(" " + "c".repeat(200) + " ")))
                .doesNotThrowAnyException();
        assertValidation(() -> service.create(TEAM_LEADER_MANTENIMIENTO, withComment("c".repeat(201))),
                "machineRef.comment");
    }

    // =====================================================================================================
    // Edición (REQ-32 a REQ-38, REQ-46)
    // =====================================================================================================

    @Test
    void updateChangesOnlyTheThreeFieldsAndKeepsTheRestOfTheOrder() {
        WorkOrder before = existing(1L, WorkOrderStatus.IN_PROGRESS, 3L);

        WorkOrder updated = service.update(ADMINISTRADOR, "1", update("  Nuevo título  ", "  Nueva descripción larga  ",
                "high"));

        ArgumentCaptor<WorkOrder> saved = ArgumentCaptor.forClass(WorkOrder.class);
        verify(orders).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(new WorkOrder(1L, "Nuevo título", "Nueva descripción larga",
                before.machineRef(), before.type(), Priority.HIGH, before.status(), before.createdAt(),
                before.takenBy(), before.closingNote()));
        assertThat(updated.title()).isEqualTo("Nuevo título");
    }

    @ParameterizedTest
    @EnumSource(WorkOrderStatus.class)
    void updateWorksInAnyStatusAndKeepsStatusOwnerAndClosingNote(WorkOrderStatus status) {
        WorkOrder before = existing(1L, status, 3L);
        lenient().when(orders.findById(1L)).thenReturn(Optional.of(before));

        service.update(TEAM_LEADER_MANTENIMIENTO, "1", update("Nuevo título", "Nueva descripción larga", "low"));

        WorkOrder saved = savedOrder();
        assertThat(saved.status()).isEqualTo(status);
        assertThat(saved.takenBy()).isEqualTo(before.takenBy());
        assertThat(saved.closingNote()).isEqualTo(before.closingNote());
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void updateIsForAdministratorAndTeamLeaderOnly(Role role) {
        if (role == ADMINISTRADOR || role == TEAM_LEADER_MANTENIMIENTO) {
            assertThatCode(() -> service.update(role, "1", update("Título", "Descripción larga", "low")))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> service.update(role, "1", update("Título", "Descripción larga", "low")))
                    .isInstanceOf(ForbiddenOperationException.class);
            verifyNoInteractions(orders);
        }
    }

    @Test
    void updateValidatesTitleDescriptionAndPriorityLikeCreate() {
        assertValidation(() -> service.update(ADMINISTRADOR, "1", update("ab", "Descripción larga", "low")), "title");
        assertValidation(() -> service.update(ADMINISTRADOR, "1", update("t".repeat(151), "Descripción larga", "low")),
                "title");
        assertValidation(() -> service.update(ADMINISTRADOR, "1", update("Título", "d".repeat(9), "low")), "description");
        assertValidation(() -> service.update(ADMINISTRADOR, "1", update("Título", "d".repeat(2001), "low")),
                "description");
        assertValidation(() -> service.update(ADMINISTRADOR, "1", update("Título", "Descripción larga", "urgent")),
                "priority");
        assertValidation(() -> service.update(ADMINISTRADOR, "1", update(null, null, null)),
                "title", "description", "priority");
        verify(orders, never()).save(any());
    }

    @Test
    void aDifferentTypeIsAValidationErrorOnType() {
        assertValidation(() -> service.update(ADMINISTRADOR, "1", withType("preventivo")), "type");
        verify(orders, never()).save(any());
    }

    @Test
    void theSameTypeAnAbsentOrANullTypeIsAccepted() {
        assertThatCode(() -> service.update(ADMINISTRADOR, "1", withType("correctivo"))).doesNotThrowAnyException();
        assertThatCode(() -> service.update(ADMINISTRADOR, "1", withType(null))).doesNotThrowAnyException();
    }

    @Test
    void aDifferentMachineIsAValidationErrorOnMachineId() {
        assertValidation(() -> service.update(ADMINISTRADOR, "1", new UpdateWorkOrderCommand("Título",
                "Descripción larga", "low", null, "2", false, null, null)), "machineRef.machineId");
        assertThatCode(() -> service.update(ADMINISTRADOR, "1", new UpdateWorkOrderCommand("Título",
                "Descripción larga", "low", null, "1", false, null, null))).doesNotThrowAnyException();
    }

    @Test
    void aDifferentPartIsAValidationErrorOnPartIdEvenIfItIsNull() {
        // La orden 1 tiene la parte 3.
        assertValidation(() -> service.update(ADMINISTRADOR, "1", new UpdateWorkOrderCommand("Título",
                "Descripción larga", "low", null, null, true, "9", null)), "machineRef.partId");
        assertValidation(() -> service.update(ADMINISTRADOR, "1", new UpdateWorkOrderCommand("Título",
                "Descripción larga", "low", null, null, true, null, null)), "machineRef.partId");
        // Igual o ausente se acepta.
        assertThatCode(() -> service.update(ADMINISTRADOR, "1", new UpdateWorkOrderCommand("Título",
                "Descripción larga", "low", null, null, true, "3", null))).doesNotThrowAnyException();
        assertThatCode(() -> service.update(ADMINISTRADOR, "1", new UpdateWorkOrderCommand("Título",
                "Descripción larga", "low", null, null, false, null, null))).doesNotThrowAnyException();
    }

    @Test
    void aNullPartSentToAnOrderWithoutPartIsNotAChange() {
        // La orden 2 es sobre la máquina completa (partId nulo).
        assertThatCode(() -> service.update(ADMINISTRADOR, "2", new UpdateWorkOrderCommand("Título",
                "Descripción larga", "low", null, null, true, null, null))).doesNotThrowAnyException();
        assertValidation(() -> service.update(ADMINISTRADOR, "2", new UpdateWorkOrderCommand("Título",
                "Descripción larga", "low", null, null, true, "3", null)), "machineRef.partId");
    }

    @Test
    void aDifferentCommentIsAValidationErrorOnComment() {
        assertValidation(() -> service.update(ADMINISTRADOR, "1", new UpdateWorkOrderCommand("Título",
                "Descripción larga", "low", null, null, false, null, "Otro comentario")), "machineRef.comment");
        // El de la orden 1 es "Comentario original"; igual (incluso con espacios) o ausente se acepta.
        assertThatCode(() -> service.update(ADMINISTRADOR, "1", new UpdateWorkOrderCommand("Título",
                "Descripción larga", "low", null, null, false, null, "  Comentario original  ")))
                .doesNotThrowAnyException();
    }

    @Test
    void everyAttemptToChangeIsReportedTogether() {
        assertValidation(() -> service.update(ADMINISTRADOR, "1", new UpdateWorkOrderCommand("Título",
                "Descripción larga", "low", "preventivo", "2", true, "9", "x")),
                "type", "machineRef.machineId", "machineRef.partId", "machineRef.comment");
    }

    @Test
    void aWholeOrderSentBackUnchangedIsAccepted() {
        // Como manda el frontend: el objeto completo con los mismos valores.
        assertThatCode(() -> service.update(ADMINISTRADOR, "1", new UpdateWorkOrderCommand("Título",
                "Descripción larga", "low", "correctivo", "1", true, "3", "Comentario original")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "abc", "-1"})
    void updateOfAnUnknownOrNonNumericOrderIsNotFoundAndSavesNothing(String id) {
        assertThatThrownBy(() -> service.update(ADMINISTRADOR, id, update("Título", "Descripción larga", "low")))
                .isInstanceOf(NotFoundException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void anInvalidBodyOnAnUnknownOrderIs400BeforeTheNotFound() {
        assertValidation(() -> service.update(ADMINISTRADOR, "999", update("a", "b", "x")),
                "title", "description", "priority");
    }

    @Test
    void anAttemptToChangeTheTypeOnAnUnknownOrderIsNotFoundBeforeThe400() {
        assertThatThrownBy(() -> service.update(ADMINISTRADOR, "999", new UpdateWorkOrderCommand("Título",
                "Descripción larga", "low", "preventivo", "2", true, "9", "x"))).isInstanceOf(NotFoundException.class);
    }

    // =====================================================================================================
    // Baja (REQ-39, REQ-40, REQ-41)
    // =====================================================================================================

    @ParameterizedTest
    @EnumSource(WorkOrderStatus.class)
    void deleteRemovesAnOrderInAnyStatus(WorkOrderStatus status) {
        lenient().when(orders.findById(1L)).thenReturn(Optional.of(existing(1L, status, 3L)));

        service.delete(ADMINISTRADOR, "1");

        verify(orders).deleteById(1L);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void deleteIsForAdministratorOnly(Role role) {
        if (role == ADMINISTRADOR) {
            assertThatCode(() -> service.delete(role, "1")).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> service.delete(role, "1")).isInstanceOf(ForbiddenOperationException.class);
            verifyNoInteractions(orders);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "abc", "-1"})
    void deleteOfAnUnknownOrNonNumericOrderIsNotFound(String id) {
        assertThatThrownBy(() -> service.delete(ADMINISTRADOR, id)).isInstanceOf(NotFoundException.class);
        verify(orders, never()).deleteById(anyLong());
    }

    // =====================================================================================================
    // Ayudas
    // =====================================================================================================

    private WorkOrder savedOrder() {
        ArgumentCaptor<WorkOrder> saved = ArgumentCaptor.forClass(WorkOrder.class);
        verify(orders).save(saved.capture());
        return saved.getValue();
    }

    private static WorkOrderQuery query(String page, String size, String title, String status, String priority) {
        return new WorkOrderQuery(page, size, title, status, priority);
    }

    private static CreateWorkOrderCommand validCreate(String type, String machineId, String partId) {
        return new CreateWorkOrderCommand("Revisar motor", "Descripción válida del problema", type, "medium", true,
                machineId, partId, "");
    }

    private static CreateWorkOrderCommand withTitle(String title) {
        return new CreateWorkOrderCommand(title, "Descripción válida del problema", "correctivo", "low", true, "1",
                null, null);
    }

    private static CreateWorkOrderCommand withDescription(String description) {
        return new CreateWorkOrderCommand("Título válido", description, "correctivo", "low", true, "1", null, null);
    }

    private static CreateWorkOrderCommand withComment(String comment) {
        return new CreateWorkOrderCommand("Título válido", "Descripción válida del problema", "correctivo", "low",
                true, "1", null, comment);
    }

    private static UpdateWorkOrderCommand update(String title, String description, String priority) {
        return new UpdateWorkOrderCommand(title, description, priority, null, null, false, null, null);
    }

    private static UpdateWorkOrderCommand withType(String type) {
        return new UpdateWorkOrderCommand("Título", "Descripción larga", "low", type, null, false, null, null);
    }

    private static WorkOrder existing(long id, WorkOrderStatus status, Long partId) {
        TakenBy owner = status == WorkOrderStatus.PENDING ? null
                : new TakenBy(7L, "Técnico Mecánico de Guardia", Instant.parse("2026-08-03T15:00:00Z"));
        boolean closed = status == WorkOrderStatus.COMPLETED || status == WorkOrderStatus.CANCELLED;
        ClosingNote note = closed ? new ClosingNote("Se resolvió el problema y se verificó el equipo.", 7L,
                "Técnico Mecánico de Guardia", Instant.parse("2026-08-03T20:00:00Z")) : null;
        String breadcrumb = partId == null ? "Envasadora línea 1"
                : "Envasadora línea 1 > Mesa de transporte > Cinta 1 > Motor de cinta";
        String comment = partId == null ? "" : "Comentario original";
        return new WorkOrder(id, "Orden existente", "Descripción existente", new MachineRef(1L, partId, breadcrumb,
                comment), WorkOrderType.CORRECTIVO, Priority.MEDIUM, status, CREATED, owner, note);
    }

    private static void assertValidation(Runnable operation, String... keys) {
        Throwable failure = catchThrowable(operation::run);
        assertThat(failure).isInstanceOf(ValidationFailedException.class);
        Map<String, String> details = ((ValidationFailedException) failure).details();
        assertThat(details).containsOnlyKeys(keys);
    }

    private static void assertReference(Runnable operation, String code) {
        assertThat(catchThrowable(operation::run)).isInstanceOfSatisfying(InvalidReferenceException.class,
                failure -> assertThat(failure.code()).isEqualTo(code));
    }
}
