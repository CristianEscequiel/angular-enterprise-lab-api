package com.enterpriselab.api.shared.domain;

import java.util.List;
import java.util.function.Function;

/**
 * Una página de resultados. {@code totalItems} cuenta todo lo que cumple los
 * criterios, no solo esta página, y {@code totalPages} sale de él: una lista
 * vacía tiene {@code 0} páginas.
 */
public record PageResult<T>(List<T> items, int page, int size, long totalItems) {

    public int totalPages() {
        return (int) ((totalItems + size - 1) / size);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalItems);
    }
}
