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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * mismo 401, con el mismo cuerpo salvo {@code timestamp}. Enmienda 00-A
 * (REQ-17 a REQ-24): el login devuelve {@code {token, user}} y {@code GET
 * /auth/me} el mismo {@code user}, leído de la base. Usa el seed (perfil
 * {@code dev}, contraseñas de db.json del frontend).
 */
@ActiveProfiles("dev")
@SuppressWarnings({"rawtypes", "unchecked"})
class AuthControllerIT extends AbstractPostgresIT {

    private static final List<String> STAFF_KEYS = List.of("id", "username", "displayName", "email", "role");

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // --- Login y credenciales (spec 00) ------------------------------------------------------

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

    /** Las contraseñas de la tabla del README (tarea 21) deben ser las del seed. */
    @Test
    void everySeedUserDocumentedInTheReadmeCanLogIn() {
        for (String[] credentials : new String[][] {
                {"admin", "admin123"}, {"teamleader", "teamleader123"}, {"produccion", "produccion123"},
                {"tecnico", "tecnico123"}, {"electricista", "electricista123"}}) {
            assertThat(login(credentials[0], credentials[1], LoginResponse.class).getStatusCode())
                    .as(credentials[0]).isEqualTo(HttpStatus.OK);
        }
    }

    // --- REQ-17: el login devuelve la sesión completa ---------------------------------------------

    @Test
    void loginReturnsTheTokenAndTheUserWithTheCommonFieldsForEveryRole() {
        Object[][] users = {
                {"admin", "admin123", "administrador", "Administrador", "admin@enterprise-lab.dev"},
                {"teamleader", "teamleader123", "team-leader-mantenimiento", "Team Leader de Mantenimiento",
                        "teamleader@enterprise-lab.dev"},
                {"produccion", "produccion123", "personal-produccion", "Personal de Producción",
                        "produccion@enterprise-lab.dev"},
                {"tecnico", "tecnico123", "tecnico", "Técnico Mecánico de Guardia", "tecnico@enterprise-lab.dev"},
                {"electricista", "electricista123", "tecnico", "Técnico Electricista Preventivo",
                        "electricista@enterprise-lab.dev"}};

        for (Object[] expected : users) {
            ResponseEntity<Map> response = login((String) expected[0], (String) expected[1], Map.class);

            assertThat(response.getStatusCode()).as((String) expected[0]).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsKeys("token", "user");
            assertThat((String) response.getBody().get("token")).isNotBlank();
            Map<String, Object> user = (Map<String, Object>) response.getBody().get("user");
            assertThat(user.get("id")).isInstanceOf(String.class);
            assertThat((String) user.get("id")).matches("[0-9]+");
            assertThat(user).containsEntry("username", expected[0]).containsEntry("role", expected[2])
                    .containsEntry("displayName", expected[3]).containsEntry("email", expected[4]);
        }
    }

    // --- REQ-18: el técnico trae su perfil del maestro ---------------------------------------------

    @Test
    void aTecnicosUserCarriesTheLegajoSpecialtyAndTeamTypeFromTheTechnicianMaster() {
        Map<String, Object> mecanico = loginUser("tecnico", "tecnico123");
        Map<String, Object> electricista = loginUser("electricista", "electricista123");

        assertThat(mecanico).containsEntry("legajo", "1001").containsEntry("specialty", "mecanico")
                .containsEntry("teamType", "guardia");
        assertThat(electricista).containsEntry("legajo", "1002").containsEntry("specialty", "electricista")
                .containsEntry("teamType", "preventivo-correctivo");
    }

    // --- REQ-19: el resto de los roles no lleva el perfil del técnico ------------------------------

    @Test
    void theOtherRolesOmitTheTechnicianFieldsInsteadOfSendingThemEmpty() {
        for (String[] credentials : new String[][] {
                {"admin", "admin123"}, {"teamleader", "teamleader123"}, {"produccion", "produccion123"}}) {
            Map<String, Object> user = loginUser(credentials[0], credentials[1]);

            assertThat(user.keySet()).as(credentials[0]).containsExactlyInAnyOrderElementsOf(STAFF_KEYS);
        }
    }

    // --- REQ-20: /auth/me devuelve el mismo usuario --------------------------------------------------

    @Test
    void meReturnsTheSameUserAsTheLoginForATechnicianAndForAStaffUser() {
        for (String[] credentials : new String[][] {{"tecnico", "tecnico123"}, {"admin", "admin123"}}) {
            ResponseEntity<Map> loginResponse = login(credentials[0], credentials[1], Map.class);
            String token = (String) loginResponse.getBody().get("token");

            ResponseEntity<Map> me = me(token);

            assertThat(me.getStatusCode()).as(credentials[0]).isEqualTo(HttpStatus.OK);
            assertThat(me.getBody()).as(credentials[0]).isEqualTo(loginResponse.getBody().get("user"));
        }
    }

    // --- REQ-21: el perfil se refresca sin volver a iniciar sesión ---------------------------------

    @Test
    void theTechnicianProfileIsRefreshedOnMeWithTheSameToken() {
        String token = (String) login("tecnico", "tecnico123", Map.class).getBody().get("token");
        assertThat(me(token).getBody()).containsEntry("teamType", "guardia");

        try {
            changeTeamTypeOfTechnician1001("preventivo-correctivo");

            assertThat(me(token).getBody()).containsEntry("teamType", "preventivo-correctivo")
                    .containsEntry("specialty", "mecanico");
        } finally {
            changeTeamTypeOfTechnician1001("guardia");
        }
        assertThat(me(token).getBody()).containsEntry("teamType", "guardia");
    }

    // --- REQ-22: nada sensible en las respuestas -------------------------------------------------------

    @Test
    void neitherTheLoginNorMeIncludeThePasswordOrItsHash() {
        ResponseEntity<String> loginResponse = login("tecnico", "tecnico123", String.class);
        String token = (String) login("tecnico", "tecnico123", Map.class).getBody().get("token");
        ResponseEntity<String> meResponse = restTemplate.exchange("/auth/me", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class);

        for (String body : List.of(loginResponse.getBody(), meResponse.getBody())) {
            assertThat(body).doesNotContainIgnoringCase("password").doesNotContain("$2").doesNotContain("tecnico123");
        }
    }

    // --- REQ-23: el token no lleva datos que pueden cambiar ------------------------------------------

    @Test
    void theTokenOfATechnicianDoesNotCarrySpecialtyOrTeamType() {
        String token = (String) login("tecnico", "tecnico123", Map.class).getBody().get("token");

        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("sub", "role", "legajo", "iat", "exp");
        assertThat(jwt.hasClaim("specialty")).isFalse();
        assertThat(jwt.hasClaim("teamType")).isFalse();
    }

    // --- REQ-24: un token válido de un usuario que ya no existe -----------------------------------------

    @Test
    void meOfAValidTokenWhoseUserNoLongerExistsIs401WithTheInvalidTokenMessage() {
        jdbcTemplate.update("insert into users (username, password_hash, display_name, email, role) "
                + "values ('usuario.efimero', ?, 'Usuario Efímero', 'efimero@enterprise-lab.dev', 'administrador')",
                passwordEncoder.encode("efimero123"));
        String token;
        try {
            token = (String) login("usuario.efimero", "efimero123", Map.class).getBody().get("token");
            assertThat(me(token).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            jdbcTemplate.update("delete from users where username = 'usuario.efimero'");
        }

        ResponseEntity<Map> deleted = me(token);
        ResponseEntity<Map> garbage = me("abc");

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(deleted.getBody()).containsEntry("code", "UNAUTHORIZED").containsEntry("path", "/auth/me")
                .containsEntry("message", garbage.getBody().get("message"));
        assertThat(deleted.getBody().toString()).doesNotContain("efimero");
    }

    // --- Tokens inválidos (spec 00) ---------------------------------------------------------------------

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

    // --- Helpers --------------------------------------------------------------------------------------------

    private Map<String, Object> loginUser(String username, String password) {
        return (Map<String, Object>) login(username, password, Map.class).getBody().get("user");
    }

    /** Cambia el tipo de equipo del técnico 1001 por la API de la spec 01, como lo haría un team leader. */
    private void changeTeamTypeOfTechnician1001(String teamType) {
        String leaderToken = (String) login("teamleader", "teamleader123", Map.class).getBody().get("token");
        HttpHeaders headers = bearer(leaderToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("firstName", "Ana", "lastName", "Ruiz", "specialty", "mecanico",
                "teamType", teamType);

        ResponseEntity<Map> response = restTemplate.exchange("/technicians/1001", HttpMethod.PUT,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> me(String token) {
        return restTemplate.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
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
