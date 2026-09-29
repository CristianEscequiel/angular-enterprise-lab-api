package com.enterpriselab.api.auth.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.enterpriselab.api.shared.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REQ-11 / REQ-13: el 403 que lanza el propio filtro de Spring Security
 * nunca llega al {@code @RestControllerAdvice}, así que este handler
 * escribe el mismo {@link ApiError} ({@code FORBIDDEN}).
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    static final String MESSAGE = "No tenés permiso para realizar esta operación";

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of("FORBIDDEN", MESSAGE, request.getRequestURI()));
    }
}
