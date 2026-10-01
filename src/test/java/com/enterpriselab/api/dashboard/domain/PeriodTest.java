package com.enterpriselab.api.dashboard.domain;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PeriodTest {

    @Test
    void fromIsTheStartOfTheFirstDayAndToExclusiveTheStartOfTheNextOne() {
        Period period = new Period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 29));

        assertThat(period.fromInstant()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(period.toExclusiveInstant()).isEqualTo(Instant.parse("2026-09-30T00:00:00Z"));
    }

    @Test
    void toExclusiveCrossesMonthAndYearEnds() {
        assertThat(new Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30)).toExclusiveInstant())
                .isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(new Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).toExclusiveInstant())
                .isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
        assertThat(new Period(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 2, 29)).toExclusiveInstant())
                .isEqualTo(Instant.parse("2028-03-01T00:00:00Z"));
    }
}
