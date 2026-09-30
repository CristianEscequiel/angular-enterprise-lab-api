package com.enterpriselab.api.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** El reloj se inyecta para que los servicios que fijan fechas (órdenes) se puedan probar con uno fijo. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
