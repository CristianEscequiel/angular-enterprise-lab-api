package com.enterpriselab.api.shared.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La entrada no cumple las reglas del dominio. El advice de {@code shared/web}
 * la traduce a {@code 400 VALIDATION_ERROR} con {@code details} por campo, en
 * el mismo formato que una falla de Bean Validation.
 */
public class ValidationFailedException extends RuntimeException {

    private final Map<String, String> details;

    public ValidationFailedException(Map<String, String> details) {
        super("La solicitud tiene datos inválidos");
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public Map<String, String> details() {
        return details;
    }
}
