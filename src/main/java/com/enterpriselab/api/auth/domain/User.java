package com.enterpriselab.api.auth.domain;

/**
 * Usuario de dominio. Sin anotaciones de JPA/Spring Data a propósito — la
 * persistencia mapea {@code UserEntity} <-> {@code User} explícitamente
 * (tarea 11).
 *
 * <p>El constructor compacto hace valer REQ-5 también del lado del
 * dominio, no solo en el {@code CHECK} de la base de datos (V1__init.sql,
 * tarea 7): un usuario {@code tecnico} siempre tiene {@code legajo}, y solo
 * un {@code tecnico} lo tiene.
 */
public record User(Long id, String username, String passwordHash, Role role, String legajo) {

    public User {
        boolean isTecnico = role == Role.TECNICO;
        boolean hasLegajo = legajo != null && !legajo.isBlank();

        if (isTecnico && !hasLegajo) {
            throw new IllegalArgumentException("Un usuario con rol tecnico debe tener legajo");
        }
        if (!isTecnico && legajo != null) {
            throw new IllegalArgumentException("Solo un usuario con rol tecnico puede tener legajo");
        }
    }
}
