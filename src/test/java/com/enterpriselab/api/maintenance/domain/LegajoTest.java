package com.enterpriselab.api.maintenance.domain;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.enterpriselab.api.shared.domain.ValidationFailedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegajoTest {

    @ParameterizedTest
    @ValueSource(strings = {"1", "1001", "0001", "12345678"})
    void acceptsBetweenOneAndEightDigits(String legajo) {
        assertThat(Legajo.isValid(legajo)).isTrue();
        assertThat(Legajo.require(legajo)).isEqualTo(legajo);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "123456789", " 1", "1 ", "1\n", "12a4", "-1", "1.5", "١٢٣"})
    void rejectsAnythingElse(String legajo) {
        assertThat(Legajo.isValid(legajo)).isFalse();
    }

    @Test
    void requireReportsTheLegajoField() {
        assertThatThrownBy(() -> Legajo.require("abc"))
                .isInstanceOfSatisfying(ValidationFailedException.class, exception ->
                        assertThat(exception.details()).containsExactly(
                                Map.entry("legajo", Legajo.INVALID_MESSAGE)));
    }
}
