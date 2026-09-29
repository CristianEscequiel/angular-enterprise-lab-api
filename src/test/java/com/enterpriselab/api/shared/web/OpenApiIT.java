package com.enterpriselab.api.shared.web;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;

import static org.assertj.core.api.Assertions.assertThat;

/** REQ-14: la documentación es pública y describe los endpoints de auth. */
@ActiveProfiles("dev")
class OpenApiIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void apiDocsListLoginAndMeWithBearerScheme() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/v3/api-docs", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> paths = (Map<String, Object>) response.getBody().get("paths");
        assertThat(paths).containsKeys("/auth/login", "/auth/me");
        Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
        assertThat((Map<String, Object>) components.get("securitySchemes")).containsKey("bearerAuth");
    }

    @Test
    void swaggerUiIsReachableWithoutToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
