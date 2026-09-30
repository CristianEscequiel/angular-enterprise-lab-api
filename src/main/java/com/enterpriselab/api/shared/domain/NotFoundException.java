package com.enterpriselab.api.shared.domain;

/** El recurso pedido no existe. {@code 404 NOT_FOUND}. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
