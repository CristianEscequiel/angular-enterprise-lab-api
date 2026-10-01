package com.enterpriselab.api.dashboard.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enterpriselab.api.auth.domain.ForbiddenOperationException;
import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.shared.domain.ValidationFailedException;
import com.enterpriselab.api.workorders.domain.Priority;
import com.enterpriselab.api.workorders.domain.WorkOrderStatus;
import com.enterpriselab.api.workorders.domain.WorkOrderType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-30T15:30:00Z");
    private static final DashboardQuery NO_PARAMS = new DashboardQuery(null, null);

    @Mock
    private DashboardStatistics statistics;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(statistics, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // =====================================================================================================
    // Período
    // =====================================================================================================

    @Test
    void withoutParametersThePeriodIsThe30DaysEndingToday() {
        givenSnapshot(List.of(), new ClosedStats(0, 0, null));

        DashboardSummary summary = service.summary(Role.ADMINISTRADOR, NO_PARAMS);

        assertThat(summary.period()).isEqualTo(new Period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }

    @Test
    void onlyFromCompletesToWithToday() {
        givenSnapshot(List.of(), new ClosedStats(0, 0, null));

        Period period = service.summary(Role.TECNICO, new DashboardQuery("2026-09-10", null)).period();

        assertThat(period).isEqualTo(new Period(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 30)));
    }

    @Test
    void onlyToCompletesFromWith29DaysBefore() {
        givenSnapshot(List.of(), new ClosedStats(0, 0, null));

        Period period = service.summary(Role.TECNICO, new DashboardQuery(null, "2026-03-31")).period();

        assertThat(period).isEqualTo(new Period(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 31)));
    }

    @Test
    void thePortReceivesFromAtMidnightAndToPlusOneDayAtMidnight() {
        givenSnapshot(List.of(), new ClosedStats(0, 0, null));

        service.summary(Role.ADMINISTRADOR, new DashboardQuery("2026-09-01", "2026-09-29"));

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(statistics).snapshot(from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(to.getValue()).isEqualTo(Instant.parse("2026-09-30T00:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-9-1", "2026-02-30", "2026-13-01", "abc", "", " ", "+20260-01-01", "2026/09/01",
            "26-09-01", "2026-09-01T00:00:00Z"})
    void anInvalidDateIsAValidationErrorOnThatParameter(String invalid) {
        ValidationFailedException onFrom = catchThrowableOfType(ValidationFailedException.class,
                () -> service.summary(Role.ADMINISTRADOR, new DashboardQuery(invalid, null)));
        ValidationFailedException onTo = catchThrowableOfType(ValidationFailedException.class,
                () -> service.summary(Role.ADMINISTRADOR, new DashboardQuery(null, invalid)));

        assertThat(onFrom.details()).containsOnlyKeys("from");
        assertThat(onTo.details()).containsOnlyKeys("to");
        verifyNoInteractions(statistics);
    }

    @Test
    void bothInvalidAreReportedTogether() {
        ValidationFailedException failure = catchThrowableOfType(ValidationFailedException.class,
                () -> service.summary(Role.ADMINISTRADOR, new DashboardQuery("x", "y")));

        assertThat(failure.details()).containsOnlyKeys("from", "to");
    }

    @Test
    void fromAfterToIsAnErrorOnFrom() {
        ValidationFailedException failure = catchThrowableOfType(ValidationFailedException.class,
                () -> service.summary(Role.ADMINISTRADOR, new DashboardQuery("2026-09-10", "2026-09-09")));

        assertThat(failure.details()).containsOnlyKeys("from");
    }

    @Test
    void aFutureFromWithTheDefaultToIsAnErrorOnFrom() {
        ValidationFailedException failure = catchThrowableOfType(ValidationFailedException.class,
                () -> service.summary(Role.ADMINISTRADOR, new DashboardQuery("2026-10-01", null)));

        assertThat(failure.details()).containsOnlyKeys("from");
    }

    @Test
    void aSingleDayPeriodIsValid() {
        givenSnapshot(List.of(), new ClosedStats(0, 0, null));

        Period period = service.summary(Role.ADMINISTRADOR, new DashboardQuery("2026-09-10", "2026-09-10")).period();

        assertThat(period.from()).isEqualTo(period.to());
    }

    @Test
    void theRoleIsCheckedBeforeTheParameters() {
        assertThatThrownBy(() -> service.summary(null, new DashboardQuery("x", "y")))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(statistics);
    }

    // =====================================================================================================
    // Conteos
    // =====================================================================================================

    @Test
    void emptyDatabaseGivesZerosInEveryKeyAndNullAverage() {
        givenSnapshot(List.of(), new ClosedStats(0, 0, null));

        DashboardSummary summary = service.summary(Role.ADMINISTRADOR, NO_PARAMS);

        assertThat(summary.byStatus()).containsExactly(
                entry(WorkOrderStatus.PENDING, 0), entry(WorkOrderStatus.IN_PROGRESS, 0),
                entry(WorkOrderStatus.COMPLETED, 0), entry(WorkOrderStatus.CANCELLED, 0));
        assertThat(summary.byPriority()).containsExactly(
                entry(Priority.LOW, 0), entry(Priority.MEDIUM, 0), entry(Priority.HIGH, 0));
        assertThat(summary.byType()).containsExactly(
                entry(WorkOrderType.PREVENTIVO, 0), entry(WorkOrderType.CORRECTIVO, 0),
                entry(WorkOrderType.PRONTO_INTERVENCION, 0));
        assertThat(summary.total()).isZero();
        assertThat(summary.open()).isZero();
        assertThat(summary.closedInPeriod()).isEqualTo(new ClosedInPeriod(0, 0, 0));
        assertThat(summary.averageResolutionMinutes()).isNull();
    }

    @Test
    void theThreeAxesTotalAndOpenAreSummedFromTheRows() {
        givenSnapshot(List.of(
                new OrderCount(WorkOrderStatus.PENDING, Priority.LOW, WorkOrderType.PREVENTIVO, 3),
                new OrderCount(WorkOrderStatus.PENDING, Priority.HIGH, WorkOrderType.CORRECTIVO, 2),
                new OrderCount(WorkOrderStatus.IN_PROGRESS, Priority.HIGH, WorkOrderType.CORRECTIVO, 4),
                new OrderCount(WorkOrderStatus.COMPLETED, Priority.MEDIUM, WorkOrderType.PRONTO_INTERVENCION, 5),
                new OrderCount(WorkOrderStatus.CANCELLED, Priority.LOW, WorkOrderType.PREVENTIVO, 1)),
                new ClosedStats(0, 0, null));

        DashboardSummary summary = service.summary(Role.PERSONAL_PRODUCCION, NO_PARAMS);

        assertThat(summary.byStatus()).containsEntry(WorkOrderStatus.PENDING, 5L)
                .containsEntry(WorkOrderStatus.IN_PROGRESS, 4L).containsEntry(WorkOrderStatus.COMPLETED, 5L)
                .containsEntry(WorkOrderStatus.CANCELLED, 1L);
        assertThat(summary.byPriority()).containsEntry(Priority.LOW, 4L).containsEntry(Priority.MEDIUM, 5L)
                .containsEntry(Priority.HIGH, 6L);
        assertThat(summary.byType()).containsEntry(WorkOrderType.PREVENTIVO, 4L)
                .containsEntry(WorkOrderType.CORRECTIVO, 6L).containsEntry(WorkOrderType.PRONTO_INTERVENCION, 5L);
        assertThat(summary.total()).isEqualTo(15);
        assertThat(summary.open()).isEqualTo(9);
    }

    @Test
    void closedInPeriodTotalIsTheSumOfBoth() {
        givenSnapshot(List.of(), new ClosedStats(7, 2, new BigDecimal("30")));

        assertThat(service.summary(Role.ADMINISTRADOR, NO_PARAMS).closedInPeriod())
                .isEqualTo(new ClosedInPeriod(7, 2, 9));
    }

    // =====================================================================================================
    // Promedio
    // =====================================================================================================

    @ParameterizedTest
    @ValueSource(strings = {"10.25:10.3", "10.24:10.2", "10.0:10.0", "10.05:10.1", "0.04:0.0", "1439.999:1440.0"})
    void theAverageIsRoundedHalfUpToOneDecimal(String pair) {
        String[] parts = pair.split(":");
        givenSnapshot(List.of(), new ClosedStats(1, 0, new BigDecimal(parts[0])));

        BigDecimal average = service.summary(Role.ADMINISTRADOR, NO_PARAMS).averageResolutionMinutes();

        assertThat(average).isEqualByComparingTo(parts[1]);
        assertThat(average.scale()).isEqualTo(1);
    }

    // =====================================================================================================
    // Carga de trabajo
    // =====================================================================================================

    @Test
    void workloadIsOrderedByCountDescendingThenNameThenId() {
        when(statistics.inProgressByOwner()).thenReturn(List.of(
                new OwnerLoad(9L, "Beto", 2), new OwnerLoad(3L, "ana", 2), new OwnerLoad(7L, "Zoe", 5),
                new OwnerLoad(2L, "Beto", 2), new OwnerLoad(1L, "Carla", 1)));

        List<WorkloadItem> workload = service.workload(Role.TEAM_LEADER_MANTENIMIENTO);

        assertThat(workload).extracting(WorkloadItem::takenById).containsExactly(7L, 3L, 2L, 9L, 1L);
        assertThat(workload.get(0)).isEqualTo(new WorkloadItem(7L, "Zoe", 5));
    }

    @Test
    void workloadIsEmptyWhenNothingIsInProgress() {
        when(statistics.inProgressByOwner()).thenReturn(List.of());

        assertThat(service.workload(Role.ADMINISTRADOR)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"TECNICO", "PERSONAL_PRODUCCION"})
    void workloadIsForbiddenWithoutQueryingForOtherRoles(Role role) {
        assertThatThrownBy(() -> service.workload(role)).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(statistics);
    }

    // --- Ayudas ----------------------------------------------------------------------------------------

    private void givenSnapshot(List<OrderCount> counts, ClosedStats closed) {
        when(statistics.snapshot(any(), any())).thenReturn(new StatisticsSnapshot(counts, closed));
    }

    private static <K> java.util.Map.Entry<K, Long> entry(K key, long value) {
        return java.util.Map.entry(key, value);
    }
}
