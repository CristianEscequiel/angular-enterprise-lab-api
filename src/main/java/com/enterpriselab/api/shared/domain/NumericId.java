package com.enterpriselab.api.shared.domain;

import java.util.OptionalLong;

/**
 * Los ids viajan como string (ROADMAP D2). Uno que no es un número entero de
 * hasta 18 dígitos ASCII (para no desbordar un {@code long}) se trata como un
 * recurso que no existe, igual que los equipos de la spec 01 (REQ-41), las
 * máquinas y partes de la spec 02 y las órdenes de la spec 03.
 */
public final class NumericId {

    private NumericId() {
    }

    public static OptionalLong parse(String id) {
        if (id == null || !id.matches("[0-9]{1,18}")) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(id));
    }
}
