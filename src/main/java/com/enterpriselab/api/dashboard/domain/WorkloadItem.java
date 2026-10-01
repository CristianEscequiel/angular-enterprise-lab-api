package com.enterpriselab.api.dashboard.domain;

/** Un técnico con órdenes en progreso y cuántas tiene. */
public record WorkloadItem(long takenById, String takenByName, long inProgress) {
}
