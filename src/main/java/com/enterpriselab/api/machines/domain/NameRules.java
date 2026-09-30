package com.enterpriselab.api.machines.domain;

import java.util.Map;

/** Regla de {@code name} compartida por máquinas y partes: obligatorio, recortado y de hasta 100 caracteres. */
final class NameRules {

    static final int MAX_LENGTH = 100;
    static final String REQUIRED_MESSAGE = "Es obligatorio";
    static final String TOO_LONG_MESSAGE = "Debe tener como máximo " + MAX_LENGTH + " caracteres";

    private NameRules() {
    }

    /** Devuelve el nombre recortado; si no es válido anota el error en {@code name} y devuelve {@code null}. */
    static String validate(String name, Map<String, String> errors) {
        String stripped = name == null ? null : name.strip();
        if (stripped == null || stripped.isEmpty()) {
            errors.put("name", REQUIRED_MESSAGE);
            return null;
        }
        if (stripped.length() > MAX_LENGTH) {
            errors.put("name", TOO_LONG_MESSAGE);
            return null;
        }
        return stripped;
    }

    static String plural(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }
}
