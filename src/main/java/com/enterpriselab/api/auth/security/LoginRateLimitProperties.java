package com.enterpriselab.api.auth.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Spec 06, REQ-4: {@code app.security.login-rate-limit.*}. Un valor cero o negativo es un error
 * de configuración: falla al arrancar nombrando la propiedad.
 */
@ConfigurationProperties(prefix = "app.security.login-rate-limit")
public record LoginRateLimitProperties(
        @DefaultValue("5") int maxAttemptsPerUser,
        @DefaultValue("20") int maxAttemptsPerIp,
        @DefaultValue("15m") Duration window) {

    private static final String PREFIX = "app.security.login-rate-limit.";

    public LoginRateLimitProperties {
        if (maxAttemptsPerUser <= 0) {
            throw new IllegalArgumentException(PREFIX + "max-attempts-per-user debe ser mayor que cero");
        }
        if (maxAttemptsPerIp <= 0) {
            throw new IllegalArgumentException(PREFIX + "max-attempts-per-ip debe ser mayor que cero");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException(PREFIX + "window debe ser una duración mayor que cero");
        }
    }
}
