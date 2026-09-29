package com.enterpriselab.api.auth.security;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-9 / REQ-10 / REQ-13: una ruta protegida sin token, o con un token
 * inválido, devuelve el mismo 401 con forma {@code ApiError}.
 */
@ActiveProfiles("dev")
class SecurityConfigIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void protectedRouteWithoutTokenReturnsApiError401() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/protected", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("code", "UNAUTHORIZED")
                .containsEntry("message", JsonAuthenticationEntryPoint.MESSAGE)
                .containsEntry("path", "/protected")
                .containsKey("timestamp");
    }

    @Test
    @SuppressWarnings("unchecked")
    void malformedTokenReturnsTheSame401Message() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("abc");
        ResponseEntity<Map> response = restTemplate.exchange("/protected", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("message", JsonAuthenticationEntryPoint.MESSAGE);
    }
}
