package com.enterpriselab.api.dashboard.domain;

/** Órdenes cerradas dentro del período; {@code total} es la suma de las dos. */
public record ClosedInPeriod(long completed, long cancelled, long total) {
}
