package com.enterpriselab.api.auth.domain;

/**
 * Resultado de un login exitoso (REQ-17): el token y el usuario completo, para
 * que el controller arme {@code {token, user}} sin volver a consultar la base.
 * El {@link User} incluye {@code passwordHash}; la capa web no lo serializa
 * directamente, arma un {@code UserResponse} con los campos públicos (REQ-22).
 */
public record AuthSession(String token, User user) {
}
