package com.enterpriselab.api.auth.web;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.enterpriselab.api.AbstractPostgresIT;
import com.enterpriselab.api.auth.domain.LoginAttemptLimiter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 06, tarea 5b, REQ-2.3: contra un servidor real (Tomcat), porque MockMvc no reproduce el
 * filtro ni la válvula que interpretan {@code X-Forwarded-For}. Con 5 intentos por IP, cambiar
 * esa cabecera en cada request no debe evitar el bloqueo.
 */
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "app.security.login-rate-limit.max-attempts-per-user=1000",
        "app.security.login-rate-limit.max-attempts-per-ip=5"})
class LoginRateLimitRealHttpIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private LoginAttemptLimiter limiter;
    @Autowired
    private Environment environment;

    @BeforeEach
    void resetState() {
        limiter.reset();
    }

    private ResponseEntity<Map> login(String username, String password, String forwardedFor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (forwardedFor != null) {
            headers.add("X-Forwarded-For", forwardedFor);
            headers.add("Forwarded", "for=" + forwardedFor);
        }
        return restTemplate.exchange("/auth/login", org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(Map.of("username", username, "password", password), headers), Map.class);
    }

    @Test
    void req2_3_aDifferentForwardedForOnEveryRequestDoesNotEvadeTheIpBlock() {
        for (int i = 1; i <= 5; i++) {
            ResponseEntity<Map> failed = login("usuario" + i, "incorrecta", "203.0.113." + i);
            assertThat(failed.getStatusCode().value()).isEqualTo(401);
        }

        ResponseEntity<Map> blocked = login("admin", "admin123", "198.51.100." + UUID.randomUUID().hashCode() % 200);

        assertThat(blocked.getStatusCode().value()).isEqualTo(429);
        assertThat(blocked.getBody()).containsEntry("code", "TOO_MANY_ATTEMPTS");
    }

    @Test
    void req2_3_theApplicationDoesNotTrustForwardedHeaders() {
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("none");
    }
}
