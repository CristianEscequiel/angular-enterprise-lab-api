package com.enterpriselab.api.auth.web;

/** REQ-17: la sesión completa que espera el frontend ({@code AuthSession}): el token y el usuario. */
public record LoginResponse(String token, UserResponse user) {
}
