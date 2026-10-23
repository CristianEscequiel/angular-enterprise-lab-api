package com.enterpriselab.api.auth.web;

import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.enterpriselab.api.auth.domain.AuthService;
import com.enterpriselab.api.auth.domain.AuthSession;
import com.enterpriselab.api.shared.web.ApiError;
import com.enterpriselab.api.shared.web.OpenApiConfig;

/**
 * REQ-7 / REQ-8 / REQ-17 a REQ-24: {@code POST /auth/login} y {@code GET /auth/me}.
 * Solo traducen HTTP a {@link AuthService}; una credencial inválida o un usuario
 * inexistente salen como 401 por {@code RestExceptionHandler}, sin lógica de
 * autenticación acá.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** REQ-17: {@code {token, user}}, con el perfil del usuario (y del técnico) leído en este momento. */
    @PostMapping("/login")
    @ApiResponse(responseCode = "200", description = "El token y el usuario",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(responseCode = "401", description = "Credenciales inválidas (INVALID_CREDENTIALS)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429",
            description = "Demasiados intentos fallidos del usuario o de la IP (TOO_MANY_ATTEMPTS). "
                    + "Retry-After: segundos hasta que se levante el bloqueo.",
            headers = @Header(name = "Retry-After", description = "Segundos de espera, entero mayor o igual que 1",
                    schema = @Schema(type = "integer")),
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // Spec 06, REQ-2.3: la IP es la de la conexión; X-Forwarded-For no se toma en cuenta.
        AuthSession session = authService.login(request.username(), request.password(), http.getRemoteAddr());
        return new LoginResponse(session.token(), UserResponse.from(session.user()));
    }

    /**
     * REQ-20, REQ-21, REQ-24: el mismo {@code user} del login, leído de la base en cada
     * llamada a partir del {@code sub} del token ya validado por Spring Security (REQ-9 /
     * REQ-10), no de sus claims. Si el usuario ya no existe, responde 401.
     */
    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(authService.currentUser(jwt.getSubject()));
    }
}
