package com.enterpriselab.api.dashboard.domain;

import java.util.List;

/** Conteos y cerradas leídos de la misma foto de la base. */
public record StatisticsSnapshot(List<OrderCount> counts, ClosedStats closed) {
}
