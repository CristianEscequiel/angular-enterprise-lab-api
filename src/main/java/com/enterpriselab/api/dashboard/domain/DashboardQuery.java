package com.enterpriselab.api.dashboard.domain;

/** Parámetros crudos del resumen: {@code String}, para que uno inválido sea {@code 400} y no un error de conversión. */
public record DashboardQuery(String from, String to) {
}
