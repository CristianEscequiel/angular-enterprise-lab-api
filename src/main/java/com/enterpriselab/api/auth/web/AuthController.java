package com.enterpriselab.api.auth.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.enterpriselab.api.auth.domain.AuthService;

/**
 * REQ-7 / REQ-8: {@code POST /auth/login}. Solo traduce HTTP a
 * {@link AuthService}; una credencial inválida sale como 401 por
 * {@code RestExceptionHandler}, sin lógica de autenticación acá.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return new LoginResponse(authService.login(request.username(), request.password()));
    }
}
