package com.enterpriselab.api.auth.domain;

import java.util.Arrays;

/**
 * REQ-11: la autorización por rol se decide en {@code domain}, no en el
 * controller ni en el filtro (mismo criterio que
 * {@code work-order.permissions.ts} del frontend). La capa web resuelve el
 * rol actual desde el {@code Jwt} y se lo pasa; acá solo se decide.
 */
public final class AccessPolicy {

    static final String FORBIDDEN_MESSAGE = "No tenés permiso para realizar esta operación";

    private AccessPolicy() {
    }

    public static void requireRole(Role current, Role... allowed) {
        if (current == null || Arrays.stream(allowed).noneMatch(role -> role == current)) {
            throw new ForbiddenOperationException(FORBIDDEN_MESSAGE);
        }
    }
}
