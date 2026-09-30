package com.enterpriselab.api.auth.domain;

/**
 * Usuario de dominio. Sin anotaciones de JPA/Spring Data a propósito — la
 * persistencia mapea {@code UserEntity} <-> {@code User} explícitamente
 * (tarea 11).
 *
 * <p>El constructor compacto hace valer las invariantes también del lado del
 * dominio, no solo en los {@code CHECK} de la base de datos (V1__init.sql y
 * V5__users_profile_required.sql):
 * <ul>
 *   <li>REQ-5: un usuario {@code tecnico} siempre tiene {@code legajo}, y solo
 *       un {@code tecnico} lo tiene.</li>
 *   <li>REQ-15: {@code displayName} y {@code email} son obligatorios.</li>
 *   <li>REQ-18 / REQ-19: un {@code tecnico} tiene {@code specialty} y
 *       {@code teamType}, y ningún otro rol los tiene.</li>
 * </ul>
 *
 * <p>{@code specialty} y {@code teamType} son {@code String} (el valor
 * kebab-case del maestro de técnicos) y no los enums de
 * {@code maintenance.domain}: ese paquete ya importa {@code auth.domain}, y
 * importarlo de vuelta cerraría un ciclo. Auth solo los transporta
 * (design.md §10.1).
 *
 * <p>Incluye {@code passwordHash}: es el objeto que usa el login. La capa web
 * nunca lo serializa directamente, arma un {@code UserResponse} con los campos
 * públicos (REQ-22).
 */
public record User(Long id, String username, String passwordHash, String displayName, String email,
        Role role, String legajo, String specialty, String teamType) {

    public User {
        if (isBlank(displayName)) {
            throw new IllegalArgumentException("El usuario debe tener nombre visible");
        }
        if (isBlank(email)) {
            throw new IllegalArgumentException("El usuario debe tener correo");
        }

        boolean isTecnico = role == Role.TECNICO;

        if (isTecnico && isBlank(legajo)) {
            throw new IllegalArgumentException("Un usuario con rol tecnico debe tener legajo");
        }
        if (!isTecnico && legajo != null) {
            throw new IllegalArgumentException("Solo un usuario con rol tecnico puede tener legajo");
        }
        if (isTecnico && (isBlank(specialty) || isBlank(teamType))) {
            throw new IllegalArgumentException("Un usuario con rol tecnico debe tener especialidad y tipo de equipo");
        }
        if (!isTecnico && (specialty != null || teamType != null)) {
            throw new IllegalArgumentException(
                    "Solo un usuario con rol tecnico puede tener especialidad y tipo de equipo");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
