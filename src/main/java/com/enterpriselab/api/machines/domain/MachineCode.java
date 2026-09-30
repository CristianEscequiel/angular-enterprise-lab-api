package com.enterpriselab.api.machines.domain;

import java.util.Locale;

/**
 * Código de máquina (REQ-5, REQ-6): de 1 a 20 caracteres de letras, dígitos y
 * guiones, sin empezar con guion. Se guarda sin espacios en los bordes y en
 * mayúsculas, así que {@code env-01} y {@code ENV-01} son la misma máquina.
 *
 * <p>El formato se valida sobre el texto recortado <em>antes</em> de pasarlo a
 * mayúsculas y solo con ASCII: {@code toUpperCase} convierte {@code ß} en
 * {@code SS} y dejaría pasar un carácter que el patrón no admite.
 */
public final class MachineCode {

    public static final String PATTERN = "^[A-Z0-9][A-Z0-9-]{0,19}$";
    public static final String INVALID_MESSAGE =
            "Debe tener de 1 a 20 letras, dígitos o guiones, sin empezar con guion";

    private MachineCode() {
    }

    public static boolean isValid(String code) {
        return code != null && code.strip().matches("[A-Za-z0-9][A-Za-z0-9-]{0,19}");
    }

    public static String normalize(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }
}
