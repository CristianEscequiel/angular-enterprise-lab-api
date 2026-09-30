package com.enterpriselab.api.shared.domain;

/**
 * La operación choca con el estado actual de los datos (unicidad, integridad
 * referencial). {@code 409} con el {@code code} específico que se le pase.
 */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
