package com.enterpriselab.api.auth.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.enterpriselab.api.auth.domain.User;

/**
 * El usuario de la sesión tal como lo ve el cliente (REQ-17 a REQ-20): el mismo
 * objeto en {@code POST /auth/login} y en {@code GET /auth/me}. Tiene solo los
 * campos públicos, así que la contraseña y su hash no pueden salir (REQ-22).
 *
 * <p>{@code legajo}, {@code specialty} y {@code teamType} son del técnico: para el
 * resto de los roles se <b>omiten</b> del JSON ({@code NON_NULL}) en vez de
 * enviarse en {@code null}, que es lo que exige {@code isAuthUser} del frontend
 * (REQ-19). {@code id} viaja como string (ROADMAP D2).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(String id, String username, String displayName, String email, String role,
        String legajo, String specialty, String teamType) {

    static UserResponse from(User user) {
        return new UserResponse(
                String.valueOf(user.id()),
                user.username(),
                user.displayName(),
                user.email(),
                user.role().toValue(),
                user.legajo(),
                user.specialty(),
                user.teamType());
    }
}
