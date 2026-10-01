package com.enterpriselab.api.shared.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NumericIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"1", "0", "007", "123456789012345678"})
    void validIds(String id) {
        assertThat(NumericId.parse(id)).isPresent();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "-1", "+1", "1.5", "0x1", "１", "١٢٣", "1234567890123456789", "1\n", " 1"})
    void invalidIds(String id) {
        assertThat(NumericId.parse(id)).isEmpty();
    }

    @Test
    void parsesTheValue() {
        assertThat(NumericId.parse("42").getAsLong()).isEqualTo(42L);
        assertThat(NumericId.parse("007").getAsLong()).isEqualTo(7L);
        assertThat(NumericId.parse("123456789012345678").getAsLong()).isEqualTo(123456789012345678L);
    }
}
