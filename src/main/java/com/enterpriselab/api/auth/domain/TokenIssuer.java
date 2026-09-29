package com.enterpriselab.api.auth.domain;

/**
 * Puerto de dominio para emitir el token de acceso de un usuario ya
 * autenticado. La implementación (JWT firmado) vive en {@code auth/security}
 * ({@code JwtTokenIssuer}, tarea 13), así {@link AuthService} no depende de
 * Spring Security.
 */
public interface TokenIssuer {

    String issueToken(User user);
}
