package com.enterpriselab.api.auth.domain;

/**
 * REQ-24: el token es válido y está firmado, pero su usuario ({@code sub}) ya
 * no existe en la base. El advice de {@code shared/web} la traduce a
 * {@code 401 UNAUTHORIZED} con el mismo mensaje que para un token inválido, sin
 * revelar que el usuario existió (design.md §10.1).
 */
public class UnknownSessionUserException extends RuntimeException {

    public UnknownSessionUserException(String message) {
        super(message);
    }
}
