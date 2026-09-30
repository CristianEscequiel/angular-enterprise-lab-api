package com.enterpriselab.api.maintenance.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SpecialtyTest {

    @ParameterizedTest
    @EnumSource(Specialty.class)
    void roundTripsEveryValue(Specialty specialty) {
        assertThat(Specialty.fromValue(specialty.toValue())).isEqualTo(specialty);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mecanico", "electricista", "general"})
    void valuesAreTheOnesOfTheFrontend(String value) {
        assertThat(Specialty.fromValue(value).toValue()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Mecanico", "MECANICO", " mecanico", "mecanico ", "plomero"})
    void rejectsCaseSpacesAndUnknownValues(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> Specialty.fromValue(value));
    }
}
