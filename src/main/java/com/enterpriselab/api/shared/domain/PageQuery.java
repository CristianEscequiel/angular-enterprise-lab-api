package com.enterpriselab.api.shared.domain;

/** Pedido de una página ya validado: {@code page} desde 1 y {@code size} entre 1 y 100. */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;
}
