package com.enterpriselab.api.workorders.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ida y vuelta de cada valor y rechazo de todo lo que no sea exactamente uno de ellos. */
class WorkOrderEnumsTest {

    @ParameterizedTest
    @EnumSource(WorkOrderType.class)
    void typeRoundTrips(WorkOrderType type) {
        assertThat(WorkOrderType.fromValue(type.toValue())).isEqualTo(type);
    }

    @ParameterizedTest
    @EnumSource(Priority.class)
    void priorityRoundTrips(Priority priority) {
        assertThat(Priority.fromValue(priority.toValue())).isEqualTo(priority);
    }

    @ParameterizedTest
    @EnumSource(WorkOrderStatus.class)
    void statusRoundTrips(WorkOrderStatus status) {
        assertThat(WorkOrderStatus.fromValue(status.toValue())).isEqualTo(status);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Preventivo", "PREVENTIVO", " preventivo", "preventivo ", "pronto_intervencion",
            "PRONTO_INTERVENCION", "otro"})
    void invalidTypes(String value) {
        assertThatThrownBy(() -> WorkOrderType.fromValue(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Low", "HIGH", " medium", "urgent"})
    void invalidPriorities(String value) {
        assertThatThrownBy(() -> Priority.fromValue(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Pending", "IN_PROGRESS", "in_progress", "in progress", " completed", "canceled"})
    void invalidStatuses(String value) {
        assertThatThrownBy(() -> WorkOrderStatus.fromValue(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(WorkOrderType.class)
    void theWireValuesAreTheFrontendOnes(WorkOrderType type) {
        assertThat(type.toValue()).isIn("preventivo", "correctivo", "pronto-intervencion");
    }
}
