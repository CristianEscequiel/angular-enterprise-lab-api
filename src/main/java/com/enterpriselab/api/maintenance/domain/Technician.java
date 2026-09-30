package com.enterpriselab.api.maintenance.domain;

/** Técnico del maestro de mantenimiento. {@code id} es {@code null} hasta que se guarda. */
public record Technician(Long id, String legajo, String firstName, String lastName,
        Specialty specialty, TeamType teamType) {
}
