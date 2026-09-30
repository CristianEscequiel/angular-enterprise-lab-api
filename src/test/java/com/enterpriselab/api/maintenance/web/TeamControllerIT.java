package com.enterpriselab.api.maintenance.web;

import java.util.Arrays;
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
 * REQ-20 a REQ-35 y REQ-39 a REQ-41 sobre HTTP real, con los usuarios y los
 * técnicos del seed de dev. Los equipos de prueba llevan el prefijo
 * {@code TeamControllerIT} y se limpian al terminar (la base se comparte con el
 * resto de las IT); los equipos y técnicos del seed solo se leen.
 */
@ActiveProfiles("dev")
@SuppressWarnings({"rawtypes", "unchecked"})
class TeamControllerIT extends AbstractPostgresIT {

    private static final String NAME = "TeamControllerIT";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from teams where name like 'TeamControllerIT%'");
    }

    // --- Lectura (REQ-20, REQ-21, REQ-22) ---------------------------------------------------------

    @Test
    void listReturnsTheSeededTeamsWithEveryField() {
        ResponseEntity<List> response = restTemplate.exchange("/teams", HttpMethod.GET,
                new HttpEntity<>(headers(teamLeader())), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> teams = response.getBody();
        assertThat(teams).allSatisfy(t -> assertThat(t).containsOnlyKeys("id", "name", "type", "memberLegajos"));
        assertThat(teams).extracting(t -> t.get("name")).contains("Guardia mecánica", "Preventivo eléctrico");
        assertThat(team(teams, "Guardia mecánica")).containsEntry("type", "guardia")
                .containsEntry("memberLegajos", List.of("1001"));
        assertThat(team(teams, "Preventivo eléctrico")).containsEntry("type", "preventivo-correctivo")
                .containsEntry("memberLegajos", List.of("1002"));
        assertThat(team(teams, "Guardia mecánica").get("id")).isInstanceOf(String.class);
    }

    @Test
    void getReturnsAnExistingTeamAndAnUnknownIdIs404() {
        Map<String, Object> created = create(NAME, "guardia", List.of("1001"));

        ResponseEntity<Map> found = call(HttpMethod.GET, "/teams/" + created.get("id"), teamLeader(), null);
        ResponseEntity<Map> unknown = call(HttpMethod.GET, "/teams/999999999", teamLeader(), null);

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody()).isEqualTo(created);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getBody()).containsEntry("code", "NOT_FOUND");
    }

    @Test
    void aNonNumericIdIs404OnGetPutAndDelete() {
        for (HttpMethod method : new HttpMethod[] {HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE}) {
            Object body = method == HttpMethod.PUT ? new TeamRequest(NAME, "guardia", List.of("1001")) : null;

            ResponseEntity<Map> response = call(method, "/teams/abc", teamLeader(), body);

            assertThat(response.getStatusCode()).as(method.name()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).containsEntry("code", "NOT_FOUND");
        }
    }

    // --- Alta (REQ-23, REQ-24, REQ-25) ------------------------------------------------------------

    @Test
    void createReturns201WithTheTeamAndItsId() {
        ResponseEntity<Map> response = call(HttpMethod.POST, "/teams", teamLeader(),
                new TeamRequest(NAME, "preventivo-correctivo", List.of("1002", "1001")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("name", NAME).containsEntry("type", "preventivo-correctivo")
                .containsEntry("memberLegajos", List.of("1002", "1001"));
        String id = (String) response.getBody().get("id");
        assertThat(id).matches("[0-9]+");
        assertThat(response.getHeaders().getLocation()).hasToString("/teams/" + id);
    }

    @Test
    void aBlankOrMissingNameIs400AndATeamIsNotCreated() {
        for (String name : new String[] {null, "", "   "}) {
            ResponseEntity<Map> response = call(HttpMethod.POST, "/teams", teamLeader(),
                    new TeamRequest(name, "guardia", List.of()));

            assertValidation(response, "name");
        }
        assertThat(count("select count(*) from teams where name like 'TeamControllerIT%'")).isZero();
    }

    @Test
    void theNameIsSavedTrimmedAndCappedAt100Characters() {
        ResponseEntity<Map> trimmed = call(HttpMethod.POST, "/teams", teamLeader(),
                new TeamRequest("  " + NAME + "  ", "guardia", List.of()));
        ResponseEntity<Map> tooLong = call(HttpMethod.POST, "/teams", teamLeader(),
                new TeamRequest(NAME + "x".repeat(100), "guardia", List.of()));

        assertThat(trimmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(trimmed.getBody()).containsEntry("name", NAME);
        assertValidation(tooLong, "name");
    }

    @Test
    void aMissingOrInvalidTypeIs400() {
        for (String type : new String[] {null, "", "Guardia", "otro"}) {
            assertValidation(call(HttpMethod.POST, "/teams", teamLeader(),
                    new TeamRequest(NAME, type, List.of("1001"))), "type");
        }
        assertThat(count("select count(*) from teams where name like 'TeamControllerIT%'")).isZero();
    }

    // --- Miembros: el mismo trato en POST y en PUT (REQ-26, REQ-27, REQ-28, REQ-31, REQ-40) -----------

    @Test
    void memberLegajosWithAnInvalidFormatAreRejectedOnPostAndOnPut() {
        Map<String, Object> existing = create(NAME + "-put", "guardia", List.of("1001"));

        for (List<String> members : List.<List<String>>of(List.of("abc"), List.of("123456789"),
                Arrays.asList("1001", null))) {
            assertValidation(call(HttpMethod.POST, "/teams", teamLeader(),
                    new TeamRequest(NAME, "guardia", members)), "memberLegajos");
            assertValidation(put(existing, "otro-nombre", "preventivo-correctivo", members), "memberLegajos");
        }
        assertUntouched(existing);
    }

    @Test
    void unknownMemberLegajosAreRejectedOnPostAndOnPutNamingTheLegajo() {
        Map<String, Object> existing = create(NAME + "-put", "guardia", List.of("1001"));
        List<String> members = List.of("1002", "99400999");

        ResponseEntity<Map> onPost = call(HttpMethod.POST, "/teams", teamLeader(),
                new TeamRequest(NAME, "guardia", members));
        ResponseEntity<Map> onPut = put(existing, "otro-nombre", "preventivo-correctivo", members);

        for (ResponseEntity<Map> response : List.of(onPost, onPut)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("code", "UNKNOWN_TECHNICIAN");
            assertThat((String) response.getBody().get("message")).contains("99400999").doesNotContain("1002");
        }
        assertThat(count("select count(*) from teams where name = ?", NAME)).isZero();
        assertUntouched(existing);
    }

    @Test
    void repeatedMemberLegajosAreRejectedOnPostAndOnPut() {
        Map<String, Object> existing = create(NAME + "-put", "guardia", List.of("1001"));
        List<String> members = List.of("1001", "1002", "1001");

        assertValidation(call(HttpMethod.POST, "/teams", teamLeader(), new TeamRequest(NAME, "guardia", members)),
                "memberLegajos");
        assertValidation(put(existing, "otro-nombre", "preventivo-correctivo", members), "memberLegajos");
        assertUntouched(existing);
    }

    @Test
    void aMissingMemberListIs400OnPostAndOnPutAndAnEmptyOneIsAccepted() {
        Map<String, Object> existing = create(NAME + "-put", "guardia", List.of("1001"));
        Map<String, Object> withoutTheKey = Map.of("name", NAME, "type", "guardia");

        assertValidation(call(HttpMethod.POST, "/teams", teamLeader(), withoutTheKey), "memberLegajos");
        assertValidation(call(HttpMethod.POST, "/teams", teamLeader(), new TeamRequest(NAME, "guardia", null)),
                "memberLegajos");
        assertValidation(call(HttpMethod.PUT, "/teams/" + existing.get("id"), teamLeader(), withoutTheKey),
                "memberLegajos");
        assertUntouched(existing);

        ResponseEntity<Map> empty = call(HttpMethod.POST, "/teams", teamLeader(),
                new TeamRequest(NAME, "guardia", List.of()));
        ResponseEntity<Map> emptied = put(existing, NAME + "-put", "guardia", List.of());

        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(empty.getBody()).containsEntry("memberLegajos", List.of());
        assertThat(emptied.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(emptied.getBody()).containsEntry("memberLegajos", List.of());
    }

    @Test
    void membersKeepTheSavedOrder() {
        Map<String, Object> created = create(NAME, "guardia", List.of("1003", "1001", "1002"));

        assertThat(created).containsEntry("memberLegajos", List.of("1003", "1001", "1002"));
        assertThat(get((String) created.get("id"))).containsEntry("memberLegajos", List.of("1003", "1001", "1002"));
    }

    @Test
    void theSameLegajoCanBeAMemberOfTwoTeams() {
        Map<String, Object> first = create(NAME + "-A", "guardia", List.of("1001"));
        Map<String, Object> second = create(NAME + "-B", "preventivo-correctivo", List.of("1001"));

        assertThat(get((String) first.get("id"))).containsEntry("memberLegajos", List.of("1001"));
        assertThat(get((String) second.get("id"))).containsEntry("memberLegajos", List.of("1001"));
    }

    // --- Edición (REQ-31, REQ-32) -------------------------------------------------------------------

    @Test
    void putReplacesNameTypeAndMembersKeepingOneAndReordering() {
        Map<String, Object> created = create(NAME, "guardia", List.of("1001", "1002", "1003"));

        ResponseEntity<Map> response = put(created, NAME + "-2", "preventivo-correctivo", List.of("1003", "1001"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("id", created.get("id")).containsEntry("name", NAME + "-2")
                .containsEntry("type", "preventivo-correctivo").containsEntry("memberLegajos", List.of("1003", "1001"));
        assertThat(get((String) created.get("id"))).isEqualTo(response.getBody());
    }

    @Test
    void putOfAnUnknownIdIs404AndCreatesNothing() {
        ResponseEntity<Map> response = call(HttpMethod.PUT, "/teams/999999999", teamLeader(),
                new TeamRequest(NAME, "guardia", List.of("1001")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(count("select count(*) from teams where name like 'TeamControllerIT%'")).isZero();
    }

    @Test
    void aMalformedJsonBodyIs400() {
        HttpHeaders headers = headers(teamLeader());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange("/teams", HttpMethod.POST,
                new HttpEntity<>("{esto no es json", headers), Map.class);

        assertValidation(response);
    }

    // --- Baja (REQ-33, REQ-34) ----------------------------------------------------------------------

    @Test
    void deleteReturns204AndKeepsTheTechnicians() {
        Map<String, Object> created = create(NAME, "guardia", List.of("1001", "1002"));

        ResponseEntity<Map> response = call(HttpMethod.DELETE, "/teams/" + created.get("id"), teamLeader(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(HttpMethod.GET, "/teams/" + created.get("id"), teamLeader(), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(count("select count(*) from technicians where legajo in ('1001', '1002')")).isEqualTo(2);
    }

    @Test
    void deleteOfAnUnknownIdIs404() {
        ResponseEntity<Map> response = call(HttpMethod.DELETE, "/teams/999999999", teamLeader(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- Permisos (REQ-35) --------------------------------------------------------------------------

    @Test
    void administratorProductionAndTechnicianGet403OnEveryOperation() {
        Map<String, Object> existing = create(NAME, "guardia", List.of("1001"));
        String id = (String) existing.get("id");

        for (String token : List.of(login("admin", "admin123"), login("produccion", "produccion123"),
                login("tecnico", "tecnico123"))) {
            assertForbidden(call(HttpMethod.GET, "/teams", token, null));
            assertForbidden(call(HttpMethod.GET, "/teams/" + id, token, null));
            assertForbidden(call(HttpMethod.POST, "/teams", token, new TeamRequest(NAME + "-x", "guardia", List.of())));
            assertForbidden(call(HttpMethod.PUT, "/teams/" + id, token,
                    new TeamRequest(NAME + "-x", "guardia", List.of())));
            assertForbidden(call(HttpMethod.DELETE, "/teams/" + id, token, null));
        }
        assertThat(count("select count(*) from teams where name like 'TeamControllerIT-x%'")).isZero();
        assertUntouched(existing);
    }

    @Test
    void the403WinsOverAnInvalidBodyAndANonNumericId() {
        assertForbidden(call(HttpMethod.POST, "/teams", login("admin", "admin123"), new TeamRequest("", "x", null)));
        assertForbidden(call(HttpMethod.PUT, "/teams/abc", login("tecnico", "tecnico123"),
                new TeamRequest("", "x", null)));
        assertForbidden(call(HttpMethod.DELETE, "/teams/abc", login("produccion", "produccion123"), null));
    }

    @Test
    void withoutATokenTheAnswerIs401() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/teams", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("code", "UNAUTHORIZED");
    }

    // --- Helpers ------------------------------------------------------------------------------------

    private Map<String, Object> create(String name, String type, List<String> members) {
        ResponseEntity<Map> response = call(HttpMethod.POST, "/teams", teamLeader(),
                new TeamRequest(name, type, members));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<Map> put(Map<String, Object> team, String name, String type, List<String> members) {
        return call(HttpMethod.PUT, "/teams/" + team.get("id"), teamLeader(), new TeamRequest(name, type, members));
    }

    private Map<String, Object> get(String id) {
        return call(HttpMethod.GET, "/teams/" + id, teamLeader(), null).getBody();
    }

    /** Tras un intento fallido el equipo es idéntico: ni nombre, ni tipo, ni miembros cambiaron. */
    private void assertUntouched(Map<String, Object> team) {
        assertThat(get((String) team.get("id"))).isEqualTo(team);
    }

    private static Map<String, Object> team(List<Map<String, Object>> teams, String name) {
        return teams.stream().filter(t -> name.equals(t.get("name"))).findFirst().orElseThrow();
    }

    private void assertValidation(ResponseEntity<Map> response, String... keys) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
        if (keys.length > 0) {
            assertThat((Map) response.getBody().get("details")).containsOnlyKeys((Object[]) keys);
        }
    }

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

    private static HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private String teamLeader() {
        return login("teamleader", "teamleader123");
    }

    private String login(String username, String password) {
        return restTemplate.postForEntity("/auth/login", new LoginRequest(username, password),
                LoginResponse.class).getBody().token();
    }
}
