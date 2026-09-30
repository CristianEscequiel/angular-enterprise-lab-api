package com.enterpriselab.api.maintenance.domain;

import java.util.Map;

import com.enterpriselab.api.shared.domain.ValidationFailedException;

/**
 * Formato del legajo: de 1 a 8 dígitos ASCII ({@code LEGAJO_PATTERN} del
 * frontend). Es identidad, así que no se recorta: {@code " 1001"} es inválido.
 * El legajo se maneja como {@code String} (no como número): {@code "0001"} y
 * {@code "1"} son legajos distintos.
 */
public final class Legajo {

    public static final String INVALID_MESSAGE = "Debe tener entre 1 y 8 dígitos";

    private Legajo() {
    }

    /** {@code matches} exige coincidir con todo el texto, así que un salto de línea final no pasa. */
    public static boolean isValid(String legajo) {
        return legajo != null && legajo.matches("[0-9]{1,8}");
    }

    public static String require(String legajo) {
        if (!isValid(legajo)) {
            throw new ValidationFailedException(Map.of("legajo", INVALID_MESSAGE));
        }
        return legajo;
    }
}
