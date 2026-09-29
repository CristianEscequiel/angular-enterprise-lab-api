package com.enterpriselab.api.auth.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.enterpriselab.api.shared.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REQ-9 / REQ-10 / REQ-13: los 401 del filtro de seguridad no pasan por el
 * {@code @RestControllerAdvice}, así que este entry point escribe el mismo
 * {@link ApiError}. El mensaje es siempre el mismo: no distingue token
 * ausente, expirado, mal firmado o malformado.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String MESSAGE = "Autenticación requerida o token inválido";

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of("UNAUTHORIZED", MESSAGE, request.getRequestURI()));
    }
}
