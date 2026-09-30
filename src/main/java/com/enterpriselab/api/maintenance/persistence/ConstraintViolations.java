package com.enterpriselab.api.maintenance.persistence;

import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;

/**
 * Nombre de la constraint de la base que originó una excepción de integridad.
 * Los adaptadores traducen por nombre (design.md §6) y no por tipo: cualquier
 * {@code DataIntegrityViolationException} tapada como "duplicado" ocultaría
 * errores reales, como un {@code CHECK} violado.
 */
final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /** El nombre sin esquema y en minúsculas (Postgres puede informarlo con {@code public.} adelante). */
    static Optional<String> nameOf(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                String name = violation.getConstraintName();
                return Optional.of(name.substring(name.lastIndexOf('.') + 1).toLowerCase());
            }
        }
        return Optional.empty();
    }
}
