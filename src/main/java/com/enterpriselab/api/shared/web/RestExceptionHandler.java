package com.enterpriselab.api.shared.web;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.enterpriselab.api.auth.domain.ForbiddenOperationException;
import com.enterpriselab.api.auth.domain.InvalidCredentialsException;
import com.enterpriselab.api.auth.domain.TooManyAttemptsException;
import com.enterpriselab.api.auth.domain.UnknownSessionUserException;
import com.enterpriselab.api.auth.security.JsonAuthenticationEntryPoint;
import com.enterpriselab.api.shared.domain.ConflictException;
import com.enterpriselab.api.shared.domain.InvalidReferenceException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.domain.ValidationFailedException;

/**
 * REQ-13: manejador global de excepciones — cualquier error controlado
 * responde el mismo {@link ApiError}, nunca el stacktrace por defecto de
 * Spring (reforzado también por {@code server.error.include-stacktrace:
 * never}, application.yml).
 *
 * <p>No cubre 401/403: esos los emiten {@code JsonAuthenticationEntryPoint}
 * y {@code JsonAccessDeniedHandler} (tareas 16 y 18) — el filtro de
 * seguridad corre antes del {@code DispatcherServlet}, así que sus
 * excepciones nunca llegan a un {@code @RestControllerAdvice} (design.md
 * §1). Las tareas 15 y 18 suman acá handlers específicos para las
 * excepciones de dominio ({@code InvalidCredentialsException},
 * {@code ForbiddenOperationException}) a medida que existen.
 */
@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ApiError body = ApiError.of("VALIDATION_ERROR", "La solicitud tiene datos inválidos",
                request.getRequestURI(), details.isEmpty() ? null : details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** JSON malformado o sin cuerpo: Spring lo rechaza antes de entrar al controller. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        ApiError body = ApiError.of("VALIDATION_ERROR", "El cuerpo de la solicitud no es un JSON válido",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<ApiError> handleValidationFailed(ValidationFailedException exception,
            HttpServletRequest request) {
        Map<String, String> details = exception.details();
        ApiError body = ApiError.of("VALIDATION_ERROR", exception.getMessage(), request.getRequestURI(),
                details.isEmpty() ? null : details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidReferenceException.class)
    public ResponseEntity<ApiError> handleInvalidReference(InvalidReferenceException exception,
            HttpServletRequest request) {
        ApiError body = ApiError.of(exception.code(), exception.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleDomainNotFound(NotFoundException exception, HttpServletRequest request) {
        ApiError body = ApiError.of("NOT_FOUND", exception.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException exception, HttpServletRequest request) {
        Map<String, String> details = exception.details();
        ApiError body = ApiError.of(exception.code(), exception.getMessage(), request.getRequestURI(),
                details.isEmpty() ? null : details);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException exception,
            HttpServletRequest request) {
        ApiError body = ApiError.of("INVALID_CREDENTIALS", exception.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    /**
     * Spec 06, REQ-3: login bloqueado por demasiados intentos. {@code Retry-After} en segundos
     * enteros, redondeado hacia arriba y nunca menor que 1; el mensaje es el mismo para el límite
     * por usuario y por IP.
     */
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyAttempts(TooManyAttemptsException exception,
            HttpServletRequest request) {
        long millis = exception.retryAfter().toMillis();
        long seconds = Math.max(1, (millis + 999) / 1000);
        ApiError body = ApiError.of("TOO_MANY_ATTEMPTS", exception.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(body);
    }

    /**
     * REQ-24: token válido de un usuario que ya no existe. Mismo código y mismo
     * mensaje que el entry point para un token inválido; no se usa el mensaje de
     * la excepción para no revelar que el usuario existió.
     */
    @ExceptionHandler(UnknownSessionUserException.class)
    public ResponseEntity<ApiError> handleUnknownSessionUser(UnknownSessionUserException exception,
            HttpServletRequest request) {
        ApiError body = ApiError.of("UNAUTHORIZED", JsonAuthenticationEntryPoint.MESSAGE, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenOperationException exception,
            HttpServletRequest request) {
        ApiError body = ApiError.of("FORBIDDEN", exception.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /** Spec 06, REQ-8: el recurso existe pero no con ese método; {@code Allow} lista los admitidos. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        ApiError body = ApiError.of("METHOD_NOT_ALLOWED", "Método no permitido para este recurso",
                request.getRequestURI());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (exception.getSupportedHttpMethods() != null) {
            response.allow(exception.getSupportedHttpMethods().toArray(new org.springframework.http.HttpMethod[0]));
        }
        return response.body(body);
    }

    /** Spec 06, REQ-9: {@code Content-Type} que el endpoint no acepta. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {
        ApiError body = ApiError.of("UNSUPPORTED_MEDIA_TYPE", "El tipo de contenido no está soportado",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
    }

    /** Spec 06, REQ-10.1: parámetro de consulta obligatorio ausente. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException exception,
            HttpServletRequest request) {
        ApiError body = ApiError.of("VALIDATION_ERROR", "La solicitud tiene datos inválidos",
                request.getRequestURI(), Map.of(exception.getParameterName(), "Es obligatorio"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Spec 06, REQ-10.2: parámetro que no se puede convertir al tipo esperado. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        ApiError body = ApiError.of("VALIDATION_ERROR", "La solicitud tiene datos inválidos",
                request.getRequestURI(), Map.of(exception.getName(), "Valor inválido"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        ApiError body = ApiError.of("NOT_FOUND", "Recurso no encontrado", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), exception);
        ApiError body = ApiError.of("INTERNAL_ERROR", "Ocurrió un error inesperado", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
