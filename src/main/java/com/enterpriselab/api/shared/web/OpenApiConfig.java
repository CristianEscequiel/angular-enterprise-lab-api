package com.enterpriselab.api.shared.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import org.springframework.context.annotation.Configuration;

/**
 * REQ-14: metadata de la API y esquema {@code bearerAuth} (JWT). El esquema
 * se aplica a cada endpoint protegido con
 * {@code @SecurityRequirement(name = "bearerAuth")}; la documentación se
 * genera desde anotaciones, no se mantiene a mano.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "angular-enterprise-lab API",
        version = "0.1.0",
        description = "Backend de angular-enterprise-lab: autenticación y autorización por rol."))
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
}
