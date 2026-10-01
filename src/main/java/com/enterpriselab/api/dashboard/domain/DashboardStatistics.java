package com.enterpriselab.api.dashboard.domain;

import java.time.Instant;
import java.util.List;

/** Puerto de lectura del dashboard; la implementación SQL vive en {@code persistence}. */
public interface DashboardStatistics {

    /** Los conteos de todas las órdenes y las cerradas con {@code from <= closedAt < toExclusive}, de la misma foto. */
    StatisticsSnapshot snapshot(Instant fromInclusive, Instant toExclusive);

    /** Un elemento por dueño de órdenes {@code in-progress}, sin orden definido. */
    List<OwnerLoad> inProgressByOwner();
}
