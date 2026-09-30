package com.enterpriselab.api.maintenance.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TeamTypeTest {

    @ParameterizedTest
    @EnumSource(TeamType.class)
    void roundTripsEveryValue(TeamType type) {
        assertThat(TeamType.fromValue(type.toValue())).isEqualTo(type);
    }

    @ParameterizedTest
    @ValueSource(strings = {"guardia", "preventivo-correctivo"})
    void valuesAreTheOnesOfTheFrontend(String value) {
        assertThat(TeamType.fromValue(value).toValue()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Guardia", "GUARDIA", " guardia", "guardia ", "preventivo_correctivo", "preventivo"})
    void rejectsCaseSpacesAndUnknownValues(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> TeamType.fromValue(value));
    }
}
