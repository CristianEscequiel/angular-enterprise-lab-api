package com.enterpriselab.api.auth.domain;

/**
 * REQ-11: el rol autenticado no tiene permiso para la operación. El advice
 * de {@code shared/web} la traduce a 403.
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
