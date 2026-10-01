package com.enterpriselab.api.dashboard.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Período en días UTC, los dos extremos incluidos. */
public record Period(LocalDate from, LocalDate to) {

    /** El día {@code from} a las 00:00:00Z, incluido. */
    public Instant fromInstant() {
        return from.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /** El día siguiente a {@code to} a las 00:00:00Z, excluido: así {@code to} cuenta completo. */
    public Instant toExclusiveInstant() {
        return to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
