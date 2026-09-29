package com.enterpriselab.api.auth.web;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-7 / REQ-8: login OK devuelve un token con {@code sub}/{@code role}/
 * {@code legajo}; usuario inexistente y contraseña incorrecta devuelven el
 * mismo 401, con el mismo cuerpo salvo {@code timestamp}. Usa el seed
 * (perfil {@code dev}, contraseñas de db.json del frontend).
 */
@ActiveProfiles("dev")
class AuthControllerIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void validCredentialsReturnATokenWithTheUserClaims() {
        ResponseEntity<LoginResponse> response = login("tecnico", "tecnico123", LoginResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Jwt jwt = jwtDecoder.decode(response.getBody().token());
        assertThat(jwt.getSubject()).isEqualTo("tecnico");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("tecnico");
        assertThat(jwt.getClaimAsString("legajo")).isEqualTo("1001");
    }

    @Test
    @SuppressWarnings("unchecked")
    void unknownUserAndWrongPasswordReturnTheSame401() {
        ResponseEntity<Map> wrongPassword = login("admin", "incorrecta", Map.class);
        ResponseEntity<Map> unknownUser = login("fantasma", "admin123", Map.class);

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody()).containsEntry("code", "INVALID_CREDENTIALS");
        assertThat(unknownUser.getBody()).containsEntry("code", wrongPassword.getBody().get("code"));
        assertThat(unknownUser.getBody()).containsEntry("message", wrongPassword.getBody().get("message"));
    }

    @Test
    void blankFieldsReturnAValidationError() {
        ResponseEntity<Map> response = login("", "", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
    }

    private <T> ResponseEntity<T> login(String username, String password, Class<T> responseType) {
        return restTemplate.postForEntity("/auth/login", new LoginRequest(username, password), responseType);
    }
}
