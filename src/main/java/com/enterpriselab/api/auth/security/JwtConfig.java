package com.enterpriselab.api.auth.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * REQ-7: JWT firmado con HS256 y secreto simétrico, vía
 * {@code oauth2-resource-server} (design.md §1) — la validación de firma,
 * expiración y formato la resuelve Spring Security a partir de estos
 * beans, sin un {@code JwtFilter} a mano.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey(properties)));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        return NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private SecretKeySpec secretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
