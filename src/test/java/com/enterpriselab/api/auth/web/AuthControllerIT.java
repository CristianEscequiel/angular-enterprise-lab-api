package com.enterpriselab.api.auth.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;
import com.enterpriselab.api.auth.security.JwtProperties;

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
    @Autowired
    private JwtProperties jwtProperties;

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

    @Test
    void meWithValidTokenReturnsTheUserData() {
        String token = login("tecnico", "tecnico123", LoginResponse.class).getBody().token();

        ResponseEntity<Map> response = me(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("username", "tecnico")
                .containsEntry("role", "tecnico")
                .containsEntry("legajo", "1001");
    }

    @Test
    void meOmitsLegajoForNonTechnicians() {
        String token = login("admin", "admin123", LoginResponse.class).getBody().token();

        ResponseEntity<Map> response = me(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("role", "administrador").doesNotContainKey("legajo");
    }

    @Test
    void expiredBadlySignedAndMalformedTokensReturnTheSame401() {
        Instant now = Instant.now();
        String expired = sign(jwtProperties.secret(), now.minusSeconds(7200), now.minusSeconds(3600));
        String badlySigned = sign("otro-secreto-distinto-de-32-bytes-o-mas!!", now, now.plusSeconds(3600));

        ResponseEntity<Map> expiredResponse = me(expired);
        ResponseEntity<Map> badlySignedResponse = me(badlySigned);
        ResponseEntity<Map> malformedResponse = me("abc");

        for (ResponseEntity<Map> response : List.of(expiredResponse, badlySignedResponse, malformedResponse)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).containsEntry("code", "UNAUTHORIZED")
                    .containsEntry("message", expiredResponse.getBody().get("message"));
        }
    }

    private ResponseEntity<Map> me(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private String sign(String secret, Instant issuedAt, Instant expiresAt) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("tecnico").claim("role", "tecnico").issuedAt(issuedAt).expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private <T> ResponseEntity<T> login(String username, String password, Class<T> responseType) {
        return restTemplate.postForEntity("/auth/login", new LoginRequest(username, password), responseType);
    }
}
