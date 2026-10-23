package com.enterpriselab.api.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadena de resource-server (JWT, sin sesión ni CSRF). Rutas públicas:
 * health (REQ-1), login (REQ-7) y, solo si están habilitadas ({@code springdoc.*.enabled}, es
 * decir con el perfil {@code dev}), la documentación OpenAPI (REQ-14, spec 06 REQ-5); todo lo
 * demás exige token. Los 401 (sin token o token inválido, REQ-9/10) los
 * escribe {@link JsonAuthenticationEntryPoint} con el formato de REQ-13.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            JsonAuthenticationEntryPoint entryPoint, JsonAccessDeniedHandler accessDeniedHandler,
            Environment environment) throws Exception {
        boolean apiDocs = environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false);
        boolean swaggerUi = environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false);
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/auth/login").permitAll();
                    if (swaggerUi) {
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    if (apiDocs) {
                        auth.requestMatchers("/v3/api-docs/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(Customizer.withDefaults()));
        return http.build();
    }
}
