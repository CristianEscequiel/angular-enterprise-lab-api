package com.enterpriselab.api.dashboard.web;

import java.time.LocalDate;

import com.enterpriselab.api.dashboard.domain.Period;

/** Período efectivo, en fechas {@code YYYY-MM-DD} UTC, los dos extremos incluidos. */
public record PeriodResponse(LocalDate from, LocalDate to) {

    static PeriodResponse from(Period period) {
        return new PeriodResponse(period.from(), period.to());
    }
}
