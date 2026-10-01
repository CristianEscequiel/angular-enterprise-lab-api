package com.enterpriselab.api.shared.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La operación choca con el estado actual de los datos (unicidad, integridad
 * referencial, estado de una orden). {@code 409} con el {@code code} específico
 * que se le pase y, opcionalmente, {@code details} con el estado real.
 */
public class ConflictException extends RuntimeException {

    private final String code;
    private final Map<String, String> details;

    public ConflictException(String code, String message) {
        this(code, message, Map.of());
    }

    public ConflictException(String code, String message, Map<String, String> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public String code() {
        return code;
    }

    /** Vacío si el conflicto no trae detalle. */
    public Map<String, String> details() {
        return details;
    }
}
