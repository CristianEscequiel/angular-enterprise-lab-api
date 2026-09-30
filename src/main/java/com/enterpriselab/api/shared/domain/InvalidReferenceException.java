package com.enterpriselab.api.shared.domain;

/**
 * La entrada tiene un formato válido pero referencia algo que no existe (por
 * ejemplo un legajo que no pertenece a ningún técnico). {@code 400} con el
 * {@code code} específico que se le pase.
 */
public class InvalidReferenceException extends RuntimeException {

    private final String code;

    public InvalidReferenceException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
