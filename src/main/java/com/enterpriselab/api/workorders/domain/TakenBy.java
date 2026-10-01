package com.enterpriselab.api.workorders.domain;

import java.time.Instant;

/** Dueño de una orden en progreso o cerrada; {@code name} es una foto del nombre al tomarla. La escribe la spec 04. */
public record TakenBy(long userId, String name, Instant at) {
}
