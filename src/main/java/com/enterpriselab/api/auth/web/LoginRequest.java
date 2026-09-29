package com.enterpriselab.api.auth.web;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El username es obligatorio") String username,
        @NotBlank(message = "La contraseña es obligatoria") String password) {
}
