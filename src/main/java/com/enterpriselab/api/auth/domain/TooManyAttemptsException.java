package com.enterpriselab.api.auth.domain;

import java.time.Duration;

/**
 * Spec 06, REQ-3: el login está bloqueado por demasiados intentos fallidos del usuario o de la
 * IP. El mensaje es siempre el mismo: no dice cuál de los dos límites se alcanzó ni si el
 * usuario existe.
 */
public class TooManyAttemptsException extends RuntimeException {

    public static final String MESSAGE = "Demasiados intentos fallidos. Probá de nuevo más tarde";

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super(MESSAGE);
        this.retryAfter = retryAfter;
    }

    /** Tiempo que falta para que se levante el bloqueo. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
