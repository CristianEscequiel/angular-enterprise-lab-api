package com.enterpriselab.api.machines.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** REQ-5 y REQ-6: el código se guarda recortado y en mayúsculas, y tiene un formato estricto. */
class MachineCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"ENV-01", "env-01", "  Env-01  ", "A", "0", "A-", "A--B", "AAAAAAAAAAAAAAAAAAAA", "A\n"})
    void validCodes(String code) {
        assertThat(MachineCode.isValid(code)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "-A", "A B", "A_1", "A.1", "AAAAAAAAAAAAAAAAAAAAA", "ß", "ÑANDÚ", "１２",
            "A\nB"})
    void invalidCodes(String code) {
        assertThat(MachineCode.isValid(code)).isFalse();
    }

    @Test
    void normalizeTrimsAndUppercases() {
        assertThat(MachineCode.normalize("  env-01 ")).isEqualTo("ENV-01");
        assertThat(MachineCode.normalize("ENV-01")).isEqualTo("ENV-01");
    }

    @Test
    void aNormalizedValidCodeAlwaysMatchesTheDatabasePattern() {
        assertThat(MachineCode.normalize(" sel-02 ")).matches(MachineCode.PATTERN);
    }

    @Test
    void theTurkishDotlessIDoesNotDependOnTheDefaultLocale() {
        assertThat(MachineCode.normalize("ii-1")).isEqualTo("II-1");
    }
}
