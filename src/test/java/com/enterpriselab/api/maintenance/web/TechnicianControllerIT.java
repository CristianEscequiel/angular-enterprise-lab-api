package com.enterpriselab.api.maintenance.web;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;
import com.enterpriselab.api.auth.web.LoginRequest;
import com.enterpriselab.api.auth.web.LoginResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-1 a REQ-19 y REQ-39 sobre HTTP real, con los usuarios del seed de dev.
 * Los técnicos y equipos de prueba usan legajos {@code 9930xxxx} y el prefijo
 * {@code TechControllerIT}, y se limpian al terminar: la base se comparte con el
 * resto de las IT. Los técnicos del seed (1001, 1002, 1003) solo se leen.
 */
@ActiveProfiles("dev")
@SuppressWarnings({"rawtypes", "unchecked"})
class TechnicianControllerIT extends AbstractPostgresIT {

    private static final String LEGAJO = "99300001";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from teams where name like 'TechControllerIT%'");
        jdbcTemplate.update("delete from technicians where legajo like '9930%'");
    }

    // --- Lectura (REQ-1, REQ-2, REQ-3) ------------------------------------------------------------

    @Test
    void listReturnsTheWholeCollectionWithEveryFieldForAdministratorAndTeamLeader() {
        for (String token : List.of(admin(), teamLeader())) {
            ResponseEntity<List> response = restTemplate.exchange("/technicians", HttpMethod.GET,
                    new HttpEntity<>(headers(token)), List.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<Map<String, Object>> technicians = response.getBody();
            assertThat(technicians).extracting(t -> t.get("legajo")).contains("1001", "1002", "1003");
            assertThat(technicians).allSatisfy(t ->
                    assertThat(t).containsOnlyKeys("id", "legajo", "firstName", "lastName", "specialty", "teamType"));
            assertThat(technicians.get(0).get("id")).isInstanceOf(String.class);
        }
    }

    @Test
    void getReturnsAnExistingTechnician() {
        ResponseEntity<Map> response = call(HttpMethod.GET, "/technicians/1001", teamLeader(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("legajo", "1001").containsEntry("firstName", "Ana")
                .containsEntry("lastName", "Ruiz").containsEntry("specialty", "mecanico")
                .containsEntry("teamType", "guardia");
    }

    @Test
    void getOfAnUnknownLegajoIs404WithApiError() {
        ResponseEntity<Map> response = call(HttpMethod.GET, "/technicians/99309999", admin(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "NOT_FOUND").containsEntry("path", "/technicians/99309999");
    }

    // --- Alta (REQ-4, REQ-5, REQ-6) ---------------------------------------------------------------

    @Test
    void createReturns201WithTheTechnicianAndLeavesUsersUntouched() {
        int usersBefore = count("select count(*) from users");

        ResponseEntity<Map> response = call(HttpMethod.POST, "/technicians", teamLeader(),
                new TechnicianRequest(LEGAJO, "Ana", "Ruiz", "mecanico", "guardia"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/technicians/" + LEGAJO);
        assertThat(response.getBody()).containsEntry("legajo", LEGAJO).containsEntry("firstName", "Ana")
                .containsEntry("specialty", "mecanico").containsEntry("teamType", "guardia");
        assertThat(response.getBody().get("id")).isInstanceOf(String.class);
        assertThat(count("select count(*) from users")).isEqualTo(usersBefore);
        assertThat(call(HttpMethod.GET, "/technicians/" + LEGAJO, admin(), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void createWithADuplicatedLegajoIs409AndCreatesNothing() {
        ResponseEntity<Map> response = call(HttpMethod.POST, "/technicians", admin(),
                new TechnicianRequest("1001", "Otro", "Técnico", "general", "guardia"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "DUPLICATE_LEGAJO");
        assertThat(count("select count(*) from technicians where legajo = '1001'")).isEqualTo(1);
        assertThat(get("/technicians/1001", admin()).get("firstName")).isEqualTo("Ana");
    }

    // --- Validación (REQ-7 a REQ-10, REQ-39) --------------------------------------------------------

    @Test
    void anInvalidLegajoInTheBodyOfThePostIs400OnTheLegajoField() {
        for (String legajo : new String[] {"abc", "123456789", "", " 1"}) {
            ResponseEntity<Map> response = call(HttpMethod.POST, "/technicians", admin(),
                    new TechnicianRequest(legajo, "Ana", "Ruiz", "mecanico", "guardia"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
            assertThat((Map) response.getBody().get("details")).containsKey("legajo");
        }
    }

    @Test
    void aMalformedLegajoInTheUrlIs400OnGetPutAndDeleteAndDoesNotRunTheOperation() {
        for (String legajo : new String[] {"abc", "123456789"}) {
            for (HttpMethod method : new HttpMethod[] {HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE}) {
                Object body = method == HttpMethod.PUT
                        ? new TechnicianRequest(null, "Ana", "Ruiz", "mecanico", "guardia") : null;

                ResponseEntity<Map> response = call(method, "/technicians/" + legajo, admin(), body);

                assertThat(response.getStatusCode()).as(method + " " + legajo).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
                assertThat((Map) response.getBody().get("details")).containsKey("legajo");
            }
        }
    }

    @Test
    void invalidFieldsAreReportedTogetherOnTheirOwnKeys() {
        ResponseEntity<Map> response = call(HttpMethod.POST, "/technicians", admin(),
                new TechnicianRequest(LEGAJO, "   ", null, "plomero", "Guardia"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((Map) response.getBody().get("details"))
                .containsOnlyKeys("firstName", "lastName", "specialty", "teamType");
        assertThat(count("select count(*) from technicians where legajo = ?", LEGAJO)).isZero();
    }

    @Test
    void aNameOf101CharactersIs400AndOneOf100IsAccepted() {
        ResponseEntity<Map> tooLong = call(HttpMethod.POST, "/technicians", admin(),
                new TechnicianRequest(LEGAJO, "a".repeat(101), "Ruiz", "mecanico", "guardia"));
        ResponseEntity<Map> exactly100 = call(HttpMethod.POST, "/technicians", admin(),
                new TechnicianRequest(LEGAJO, "a".repeat(100), "Ruiz", "mecanico", "guardia"));

        assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((Map) tooLong.getBody().get("details")).containsOnlyKeys("firstName");
        assertThat(exactly100.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void namesAreSavedTrimmedOnCreateAndOnUpdate() {
        ResponseEntity<Map> created = call(HttpMethod.POST, "/technicians", admin(),
                new TechnicianRequest(LEGAJO, "  Ana María ", "\tRuiz  ", "general", "guardia"));
        ResponseEntity<Map> updated = call(HttpMethod.PUT, "/technicians/" + LEGAJO, admin(),
                new TechnicianRequest(null, "  Luisa ", " Paz\t", "general", "guardia"));

        assertThat(created.getBody()).containsEntry("firstName", "Ana María").containsEntry("lastName", "Ruiz");
        assertThat(updated.getBody()).containsEntry("firstName", "Luisa").containsEntry("lastName", "Paz");
    }

    @Test
    void aMalformedJsonBodyIs400() {
        HttpHeaders headers = headers(admin());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange("/technicians", HttpMethod.POST,
                new HttpEntity<>("{esto no es json", headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
    }

    // --- Edición (REQ-11, REQ-12, REQ-13) ---------------------------------------------------------

    @Test
    void updateChangesTheFourFieldsAndKeepsTheLegajo() {
        call(HttpMethod.POST, "/technicians", admin(),
                new TechnicianRequest(LEGAJO, "Ana", "Ruiz", "mecanico", "guardia"));

        ResponseEntity<Map> response = call(HttpMethod.PUT, "/technicians/" + LEGAJO, teamLeader(),
                new TechnicianRequest(LEGAJO, "Luisa", "Paz", "electricista", "preventivo-correctivo"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("legajo", LEGAJO).containsEntry("firstName", "Luisa")
                .containsEntry("lastName", "Paz").containsEntry("specialty", "electricista")
                .containsEntry("teamType", "preventivo-correctivo");
        assertThat(get("/technicians/" + LEGAJO, admin())).containsEntry("firstName", "Luisa");
    }

    @Test
    void updateWithADifferentLegajoInTheBodyIs400AndLeavesTheTechnicianUntouched() {
        call(HttpMethod.POST, "/technicians", admin(),
                new TechnicianRequest(LEGAJO, "Ana", "Ruiz", "mecanico", "guardia"));

        ResponseEntity<Map> response = call(HttpMethod.PUT, "/technicians/" + LEGAJO, admin(),
                new TechnicianRequest("99300002", "Luisa", "Paz", "electricista", "guardia"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((Map) response.getBody().get("details")).containsKey("legajo");
        assertThat(get("/technicians/" + LEGAJO, admin())).containsEntry("firstName", "Ana");
        assertThat(count("select count(*) from technicians where legajo = '99300002'")).isZero();
    }

    @Test
    void updateOfAnUnknownLegajoIs404AndCreatesNothing() {
        ResponseEntity<Map> response = call(HttpMethod.PUT, "/technicians/" + LEGAJO, admin(),
                new TechnicianRequest(null, "Ana", "Ruiz", "mecanico", "guardia"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(count("select count(*) from technicians where legajo = ?", LEGAJO)).isZero();
    }

    // --- Baja (REQ-14 a REQ-17) ---------------------------------------------------------------------

    @Test
    void deleteOfATechnicianWithoutLoginOrTeamsIs204AndRemovesIt() {
        call(HttpMethod.POST, "/technicians", admin(),
                new TechnicianRequest(LEGAJO, "Ana", "Ruiz", "mecanico", "guardia"));

        ResponseEntity<Map> response = call(HttpMethod.DELETE, "/technicians/" + LEGAJO, admin(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(HttpMethod.GET, "/technicians/" + LEGAJO, admin(), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteOfATechnicianWithALoginIs409AndKeepsIt() {
        ResponseEntity<Map> response = call(HttpMethod.DELETE, "/technicians/1001", admin(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "TECHNICIAN_IN_USE");
        assertThat((String) response.getBody().get("message")).contains("usuario de acceso");
        assertThat(count("select count(*) from technicians where legajo = '1001'")).isEqualTo(1);
    }

    @Test
    void deleteOfATechnicianWhoIsAMemberOfTwoTeamsIs409NamingBothTeams() {
        call(HttpMethod.POST, "/technicians", admin(),
                new TechnicianRequest(LEGAJO, "Ana", "Ruiz", "mecanico", "guardia"));
        for (String name : List.of("TechControllerIT-A", "TechControllerIT-B")) {
            jdbcTemplate.update("insert into teams (name, type) values (?, 'guardia')", name);
            jdbcTemplate.update("insert into team_members (team_id, technician_id, sort_order) values "
                    + "((select id from teams where name = ?), (select id from technicians where legajo = ?), 0)",
                    name, LEGAJO);
        }

        ResponseEntity<Map> response = call(HttpMethod.DELETE, "/technicians/" + LEGAJO, admin(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "TECHNICIAN_IN_USE");
        assertThat((String) response.getBody().get("message")).contains("TechControllerIT-A", "TechControllerIT-B")
                .doesNotContain("usuario de acceso");
        assertThat(count("select count(*) from technicians where legajo = ?", LEGAJO)).isEqualTo(1);
    }

    @Test
    void deleteOfAnUnknownLegajoIs404() {
        ResponseEntity<Map> response = call(HttpMethod.DELETE, "/technicians/99309999", admin(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- Permisos (REQ-18, REQ-19) ------------------------------------------------------------------

    @Test
    void productionAndTechnicianRolesGet403OnEveryOperation() {
        for (String token : List.of(login("produccion", "produccion123"), login("tecnico", "tecnico123"))) {
            assertForbidden(call(HttpMethod.GET, "/technicians", token, null));
            assertForbidden(call(HttpMethod.GET, "/technicians/1001", token, null));
            assertForbidden(call(HttpMethod.POST, "/technicians", token,
                    new TechnicianRequest(LEGAJO, "Ana", "Ruiz", "mecanico", "guardia")));
            assertForbidden(call(HttpMethod.PUT, "/technicians/1001", token,
                    new TechnicianRequest(null, "Otro", "Nombre", "general", "guardia")));
            assertForbidden(call(HttpMethod.DELETE, "/technicians/1003", token, null));
        }
        assertThat(count("select count(*) from technicians where legajo = ?", LEGAJO)).isZero();
        assertThat(count("select count(*) from technicians where legajo = '1003'")).isEqualTo(1);
        assertThat(get("/technicians/1001", admin())).containsEntry("firstName", "Ana");
    }

    @Test
    void teamLeaderCannotDeleteAndTheTechnicianRemains() {
        call(HttpMethod.POST, "/technicians", admin(),
                new TechnicianRequest(LEGAJO, "Ana", "Ruiz", "mecanico", "guardia"));

        assertForbidden(call(HttpMethod.DELETE, "/technicians/" + LEGAJO, teamLeader(), null));

        assertThat(count("select count(*) from technicians where legajo = ?", LEGAJO)).isEqualTo(1);
    }

    @Test
    void the403WinsOverTheBadRequestOfAMalformedLegajoInTheUrl() {
        assertForbidden(call(HttpMethod.GET, "/technicians/abc", login("produccion", "produccion123"), null));
        assertForbidden(call(HttpMethod.PUT, "/technicians/abc", login("tecnico", "tecnico123"),
                new TechnicianRequest(null, "Ana", "Ruiz", "mecanico", "guardia")));
        assertForbidden(call(HttpMethod.DELETE, "/technicians/abc", teamLeader(), null));
        assertForbidden(call(HttpMethod.DELETE, "/technicians/123456789", login("produccion", "produccion123"), null));
    }

    @Test
    void withoutATokenTheAnswerIs401() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/technicians", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("code", "UNAUTHORIZED");
    }

    // --- Helpers ------------------------------------------------------------------------------------

    private void assertForbidden(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("code", "FORBIDDEN");
    }

    private ResponseEntity<Map> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = headers(token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private Map<String, Object> get(String path, String token) {
        return call(HttpMethod.GET, path, token, null).getBody();
    }

    private static HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private String admin() {
        return login("admin", "admin123");
    }

    private String teamLeader() {
        return login("teamleader", "teamleader123");
    }

    private String login(String username, String password) {
        return restTemplate.postForEntity("/auth/login", new LoginRequest(username, password),
                LoginResponse.class).getBody().token();
    }
}
