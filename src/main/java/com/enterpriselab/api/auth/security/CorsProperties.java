package com.enterpriselab.api.auth.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * REQ-12: {@code app.cors.allowed-origins}. Un {@code *} en la lista es un
 * error de configuración: falla al arrancar en vez de abrir la API a
 * cualquier origen.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        if (allowedOrigins.stream().anyMatch(origin -> origin.trim().contains("*"))) {
            throw new IllegalArgumentException(
                    "app.cors.allowed-origins no admite comodines ('*'): listá los orígenes explícitos");
        }
    }
}
