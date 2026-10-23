package com.enterpriselab.api.shared.web;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.enterpriselab.api.AbstractPostgresIT;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 06, tarea 6, REQ-5: sin el perfil {@code dev} (a propósito no lleva {@code @ActiveProfiles})
 * la documentación de la API no se publica. {@link AbstractPostgresIT} carga el seed aun sin
 * {@code dev}, así que se inicia sesión con {@code admin}.
 */
class OpenApiDisabledIT extends AbstractPostgresIT {

    private static final List<String> DOC_PATHS = List.of("/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs");

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ApplicationContext context;

    private String adminToken() {
        ResponseEntity<Map> login = restTemplate.postForEntity("/auth/login",
                Map.of("username", "admin", "password", "admin123"), Map.class);
        return (String) login.getBody().get("token");
    }

    @Test
    void req5_1_withoutATokenTheDocsAreUnauthorized() {
        for (String path : DOC_PATHS) {
            ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
            assertThat(response.getStatusCode().value()).as(path).isEqualTo(401);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void req5_1b_withAValidTokenTheDocsAreNotFoundWithTheStandardBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        for (String path : DOC_PATHS) {
            ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers),
                    Map.class);
            assertThat(response.getStatusCode().value()).as(path).isEqualTo(404);
            assertThat(response.getBody()).as(path).containsEntry("code", "NOT_FOUND").containsEntry("path", path)
                    .containsKey("timestamp");
        }
    }

    @Test
    void req5_3_theSpringdocEndpointsAreNotInTheContext() {
        assertThat(context.getBeanNamesForType(org.springdoc.webmvc.api.OpenApiWebMvcResource.class)).isEmpty();
    }
}
