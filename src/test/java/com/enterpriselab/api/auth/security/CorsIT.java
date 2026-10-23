package com.enterpriselab.api.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;

import static org.assertj.core.api.Assertions.assertThat;

/** REQ-12: preflight desde el origen del frontend sí, desde otro origen no. */
@ActiveProfiles("dev")
class CorsIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void preflightFromAllowedOriginReturnsAllowOrigin() {
        ResponseEntity<Void> response = preflight("http://localhost:4200");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:4200");
    }

    @Test
    void preflightFromOtherOriginDoesNotReturnAllowOrigin() {
        ResponseEntity<Void> response = preflight("http://evil.com");

        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    private ResponseEntity<Void> preflight(String origin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        headers.setAccessControlRequestMethod(HttpMethod.GET);
        headers.setAccessControlRequestHeaders(java.util.List.of("Authorization"));
        return restTemplate.exchange("/auth/me", HttpMethod.OPTIONS, new HttpEntity<>(headers), Void.class);
    }

    @Test
    void req3_3_aTooManyRequestsResponseExposesRetryAfterToAnAllowedOrigin() {
        ResponseEntity<java.util.Map> login = restTemplate.postForEntity("/auth/login",
                java.util.Map.of("username", "admin", "password", "admin123"), java.util.Map.class);
        String token = (String) login.getBody().get("token");
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:4200");
        headers.setBearerAuth(token);

        ResponseEntity<String> response = restTemplate.exchange("/test/too-many?millis=5000", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("5");
        assertThat(response.getHeaders().getAccessControlExposeHeaders()).contains("Retry-After");
    }
}
