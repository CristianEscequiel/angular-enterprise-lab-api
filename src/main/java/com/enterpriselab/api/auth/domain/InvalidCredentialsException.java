package com.enterpriselab.api.auth.domain;

/**
 * REQ-8: username inexistente o contraseña incorrecta responden lo mismo,
 * con el mismo mensaje — {@link AuthService} (tarea 14) la lanza en ambos
 * casos por igual, sin distinguir cuál falló.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
