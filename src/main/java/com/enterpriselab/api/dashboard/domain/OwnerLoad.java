package com.enterpriselab.api.dashboard.domain;

/** Órdenes en progreso de un dueño; {@code takenByName} es el de su toma más reciente. */
public record OwnerLoad(long takenById, String takenByName, long count) {
}
