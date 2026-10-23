package com.enterpriselab.api.shared.web;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 06, tarea 7: los errores estándar de Spring MVC contra la aplicación completa (cadena de
 * seguridad, controladores reales), con token de {@code admin}. Todos responden el {@code ApiError}
 * de siempre y nunca {@code 500}.
 */
@ActiveProfiles("dev")
class WebErrorsIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpHeaders auth;

    @BeforeEach
    void login() {
        ResponseEntity<Map> response = restTemplate.postForEntity("/auth/login",
                Map.of("username", "admin", "password", "admin123"), Map.class);
        auth = new HttpHeaders();
        auth.setBearerAuth((String) response.getBody().get("token"));
    }

    private ResponseEntity<Map> call(HttpMethod method, String path, HttpHeaders headers, Object body) {
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    @Test
    void req8_1_deleteOnTheOrdersCollectionIs405WithAllow() {
        ResponseEntity<Map> response = call(HttpMethod.DELETE, "/work-orders", auth, null);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody()).containsEntry("code", "METHOD_NOT_ALLOWED")
                .containsEntry("path", "/work-orders").containsKey("timestamp");
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET, HttpMethod.POST);
    }

    @Test
    void req9_1_aPlainTextBodyOnPostOrdersIs415() {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(auth);
        headers.setContentType(MediaType.TEXT_PLAIN);

        ResponseEntity<Map> response = call(HttpMethod.POST, "/work-orders", headers, "hola");

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getBody()).containsEntry("code", "UNSUPPORTED_MEDIA_TYPE")
                .containsEntry("path", "/work-orders");
    }

    @Test
    void req9_2_aPlainTextBodyOnLoginIs415() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);

        ResponseEntity<Map> response = call(HttpMethod.POST, "/auth/login", headers, "admin:admin123");

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getBody()).containsEntry("code", "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void req10_3_aNonNumericPageIsStill400WithThePageInDetails() {
        ResponseEntity<Map> response = call(HttpMethod.GET, "/work-orders?page=abc", auth, null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
        assertThat((Map<String, Object>) response.getBody().get("details")).containsKey("page");
    }

    @Test
    void req11_1_anUnmappedPathWithATokenIs404WithTheStandardBody() {
        ResponseEntity<Map> response = call(HttpMethod.GET, "/no-existe", auth, null);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).containsEntry("code", "NOT_FOUND").containsEntry("path", "/no-existe")
                .containsKey("timestamp");
    }

    @Test
    void req11_2_anUnmappedPathWithoutATokenIs401() {
        ResponseEntity<Map> response = call(HttpMethod.GET, "/no-existe", null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).containsEntry("code", "UNAUTHORIZED");
    }
}
