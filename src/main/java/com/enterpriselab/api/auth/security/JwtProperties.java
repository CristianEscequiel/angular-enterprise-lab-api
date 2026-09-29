package com.enterpriselab.api.auth.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.jwt.*} (application.yml, tarea 3). Sin default de
 * {@code secret} fuera del perfil {@code dev} — a propósito, para que
 * arrancar sin configurarlo explícitamente falle en vez de firmar tokens
 * con un secreto conocido.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration ttl) {
}
