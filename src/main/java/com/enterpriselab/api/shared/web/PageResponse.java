package com.enterpriselab.api.shared.web;

import java.util.List;
import java.util.function.Function;

import com.enterpriselab.api.shared.domain.PageResult;

/**
 * Respuesta paginada {@code {data, page, size, totalItems, totalPages}}: reemplaza
 * a {@code _page}, {@code _per_page} y {@code PaginatedResponse} de JSON Server
 * (ROADMAP D1).
 */
public record PageResponse<T>(List<T> data, int page, int size, long totalItems, int totalPages) {

    public static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
        return new PageResponse<>(result.items().stream().map(mapper).toList(), result.page(), result.size(),
                result.totalItems(), result.totalPages());
    }
}
