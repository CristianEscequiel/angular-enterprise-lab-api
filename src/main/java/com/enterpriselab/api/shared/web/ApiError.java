package com.enterpriselab.api.shared.web;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * REQ-13: cuerpo JSON consistente para cualquier error controlado
 * ({@code code}, {@code message}, {@code timestamp}, {@code path}).
 *
 * <p>{@code details} es la única excepción — solo lo llena
 * {@link RestExceptionHandler} para {@code VALIDATION_ERROR} (design.md
 * §5); {@code @JsonInclude(NON_NULL)} lo omite del JSON en el resto de los
 * casos, para no ensuciar la forma base con un {@code "details":null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Instant timestamp, String path, Map<String, String> details) {

    public static ApiError of(String code, String message, String path) {
        return new ApiError(code, message, Instant.now(), path, null);
    }

    public static ApiError of(String code, String message, String path, Map<String, String> details) {
        return new ApiError(code, message, Instant.now(), path, details);
    }
}
