package com.enterpriselab.api.auth.web;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.enterpriselab.api.AbstractPostgresIT;
import com.enterpriselab.api.auth.domain.AccessPolicy;
import com.enterpriselab.api.auth.domain.Role;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-11: contra un controller de test (solo en {@code src/test}) restringido
 * a {@code administrador}, un {@code tecnico} recibe 403 con {@code ApiError}
 * y un {@code administrador} recibe 200.
 */
@ActiveProfiles("dev")
@Import(RoleRestrictionIT.AdminOnlyController.class)
class RoleRestrictionIT extends AbstractPostgresIT {

    @RestController
    static class AdminOnlyController {

        @GetMapping("/test/admin-only")
        Map<String, String> adminOnly(@AuthenticationPrincipal Jwt jwt) {
            AccessPolicy.requireRole(Role.fromValue(jwt.getClaimAsString("role")), Role.ADMINISTRADOR);
            return Map.of("ok", "true");
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void technicianGets403WithApiError() {
        ResponseEntity<Map> response = adminOnly(token("tecnico", "tecnico123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("code", "FORBIDDEN")
                .containsEntry("path", "/test/admin-only");
    }

    @Test
    void administratorGets200() {
        ResponseEntity<Map> response = adminOnly(token("admin", "admin123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String token(String username, String password) {
        return restTemplate.postForEntity("/auth/login", new LoginRequest(username, password),
                LoginResponse.class).getBody().token();
    }

    private ResponseEntity<Map> adminOnly(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange("/test/admin-only", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }
}
