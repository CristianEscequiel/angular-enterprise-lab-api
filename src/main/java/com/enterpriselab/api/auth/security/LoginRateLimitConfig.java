package com.enterpriselab.api.auth.security;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.enterpriselab.api.auth.domain.LoginAttemptLimiter;

/** Spec 06: cablea el limitador de dominio con las propiedades y el reloj de la aplicación. */
@Configuration
@EnableConfigurationProperties(LoginRateLimitProperties.class)
public class LoginRateLimitConfig {

    @Bean
    public LoginAttemptLimiter loginAttemptLimiter(LoginRateLimitProperties properties, Clock clock) {
        return new LoginAttemptLimiter(properties.maxAttemptsPerUser(), properties.maxAttemptsPerIp(),
                properties.window(), clock);
    }
}
