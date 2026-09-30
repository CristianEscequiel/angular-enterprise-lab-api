package com.enterpriselab.api.machines.web;

import java.util.HashMap;
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
 * REQ-1 a REQ-15, REQ-31, REQ-32, REQ-36 y REQ-37 sobre HTTP real, con los
 * usuarios y las máquinas del seed de dev. Las máquinas de prueba llevan el
 * prefijo {@code MCIT-} en el código y se limpian al terminar (la base se
 * comparte con el resto de las IT); las máquinas y partes del seed solo se leen.
 */
@ActiveProfiles("dev")
@SuppressWarnings({"rawtypes", "unchecked"})
class MachineControllerIT extends AbstractPostgresIT {

    private static final String NAME = "MachineControllerIT";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, String> tokens = new HashMap<>();

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from parts where machine_id in (select id from machines where code like 'MCIT-%')");
        jdbcTemplate.update("delete from machines where code like 'MCIT-%'");
    }

    // --- Lectura (REQ-1, REQ-2, REQ-3, REQ-32) -----------------------------------------------------

    @Test
    void listReturnsTheSeededMachinesWithEveryFieldAndThePartCount() {
        ResponseEntity<List> response = restTemplate.exchange("/machines", HttpMethod.GET,
                new HttpEntity<>(headers(admin())), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> machines = response.getBody();
        assertThat(machines).allSatisfy(m -> assertThat(m).containsOnlyKeys("id", "code", "name", "partCount"));
        assertThat(machine(machines, "ENV-01")).containsEntry("id", "1").containsEntry("name", "Envasadora línea 1")
                .containsEntry("partCount", 7);
        assertThat(machine(machines, "SEL-02")).containsEntry("id", "2").containsEntry("partCount", 3);
        assertThat(machine(machines, "ROT-03")).containsEntry("id", "3").containsEntry("partCount", 0);
    }

    @Test
    void getReturnsAnExistingMachineWithTheSameFieldsAsTheList() {
        ResponseEntity<Map> response = call(HttpMethod.GET, "/machines/1", admin(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsOnlyKeys("id", "code", "name", "partCount")
                .containsEntry("code", "ENV-01").containsEntry("partCount", 7);
    }

    @Test
    void anUnknownOrNonNumericIdIs404WithTheApiError() {
        for (String id : new String[] {"999999999", "abc"}) {
            ResponseEntity<Map> response = call(HttpMethod.GET, "/machines/" + id, admin(), null);

            assertThat(response.getStatusCode()).as(id).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).containsEntry("code", "NOT_FOUND").containsEntry("path", "/machines/" + id)
                    .containsKeys("message", "timestamp");
        }
    }

    @Test
    void everyRoleCanRead() {
        for (String token : List.of(admin(), teamLeader(), production(), technician())) {
            assertThat(restTemplate.exchange("/machines", HttpMethod.GET, new HttpEntity<>(headers(token)),
                    List.class).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(call(HttpMethod.GET, "/machines/1", token, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void withoutATokenEveryEndpointIs401() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/machines", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Alta (REQ-4, REQ-5, REQ-6, REQ-7, REQ-8, REQ-37) ---------------------------------------------

    @Test
    void createReturns201WithTheMachineItsIdAndZeroParts() {
        ResponseEntity<Map> response = call(HttpMethod.POST, "/machines", admin(),
                new MachineRequest("MCIT-1", NAME));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("code", "MCIT-1").containsEntry("name", NAME)
                .containsEntry("partCount", 0);
        String id = (String) response.getBody().get("id");
        assertThat(id).matches("[0-9]+");
        assertThat(response.getHeaders().getLocation()).hasToString("/machines/" + id);
        assertThat(call(HttpMethod.GET, "/machines/" + id, admin(), null).getBody()).isEqualTo(response.getBody());
    }

    @Test
    void theCodeIsSavedTrimmedAndInUppercaseAndTheNameTrimmed() {
        ResponseEntity<Map> response = call(HttpMethod.POST, "/machines", teamLeader(),
                new MachineRequest("  mcit-2  ", "  " + NAME + "  "));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("code", "MCIT-2").containsEntry("name", NAME);
        assertThat(jdbcTemplate.queryForObject("select code from machines where code like 'MCIT-%'", String.class))
                .isEqualTo("MCIT-2");
    }

    @Test
    void anInvalidCodeIs400OnCodeAndNothingIsCreated() {
        for (String code : new String[] {null, "", "   ", "-MCIT", "MCIT 1", "MCIT_1", "M".repeat(21), "ß"}) {
            ResponseEntity<Map> response = call(HttpMethod.POST, "/machines", admin(), new MachineRequest(code, NAME));

            assertValidation(response, "code");
        }
        assertThat(count("select count(*) from machines where name = ?", NAME)).isZero();
    }

    @Test
    void aBlankOrMissingNameIs400OnName() {
        for (String name : new String[] {null, "", "   "}) {
            assertValidation(call(HttpMethod.POST, "/machines", admin(), new MachineRequest("MCIT-3", name)), "name");
        }
        assertThat(count("select count(*) from machines where code = 'MCIT-3'")).isZero();
    }

    @Test
    void theNameIsCappedAt100CharactersAfterTrimming() {
        ResponseEntity<Map> exactly100 = call(HttpMethod.POST, "/machines", admin(),
                new MachineRequest("MCIT-4", " " + "n".repeat(100) + " "));
        ResponseEntity<Map> tooLong = call(HttpMethod.POST, "/machines", admin(),
                new MachineRequest("MCIT-5", "n".repeat(101)));

        assertThat(exactly100.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertValidation(tooLong, "name");
        assertThat(count("select count(*) from machines where code = 'MCIT-5'")).isZero();
    }

    @Test
    void allFieldErrorsAreReportedTogether() {
        assertValidation(call(HttpMethod.POST, "/machines", admin(), new MachineRequest("!!", " ")), "code", "name");
    }

    @Test
    void aDuplicateCodeIs409AndNothingIsCreatedEvenIfOnlyTheCaseDiffers() {
        call(HttpMethod.POST, "/machines", admin(), new MachineRequest("MCIT-DUP", NAME));

        ResponseEntity<Map> response = call(HttpMethod.POST, "/machines", admin(),
                new MachineRequest("mcit-dup", "Otra"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "DUPLICATE_MACHINE_CODE");
        assertThat(count("select count(*) from machines where code = 'MCIT-DUP'")).isEqualTo(1);
    }

    // --- Edición (REQ-9, REQ-10, REQ-11, REQ-12) ---------------------------------------------------

    @Test
    void updateChangesCodeAndNameAndKeepsTheIdAndTheParts() {
        Map<String, Object> created = create("MCIT-U1", NAME);
        jdbcTemplate.update("insert into parts (machine_id, name) values (?, 'Parte')",
                Long.valueOf((String) created.get("id")));

        ResponseEntity<Map> response = call(HttpMethod.PUT, "/machines/" + created.get("id"), teamLeader(),
                new MachineRequest(" mcit-u2 ", " Renombrada "));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("id", created.get("id")).containsEntry("code", "MCIT-U2")
                .containsEntry("name", "Renombrada").containsEntry("partCount", 1);
    }

    @Test
    void aMachineIsNotADuplicateOfItselfEvenIfOnlyTheCaseOfTheCodeChanges() {
        Map<String, Object> created = create("MCIT-U3", NAME);

        ResponseEntity<Map> onlyName = put(created, "MCIT-U3", "Solo el nombre");
        ResponseEntity<Map> onlyCase = put(created, "mcit-u3", "Solo el nombre");

        assertThat(onlyName.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(onlyCase.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(onlyCase.getBody()).containsEntry("code", "MCIT-U3");
    }

    @Test
    void updateWithTheCodeOfAnotherMachineIs409AndTheMachineIsUntouched() {
        create("MCIT-U4", NAME);
        Map<String, Object> other = create("MCIT-U5", NAME);

        ResponseEntity<Map> response = put(other, "mcit-u4", "Cambiada");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "DUPLICATE_MACHINE_CODE");
        assertThat(call(HttpMethod.GET, "/machines/" + other.get("id"), admin(), null).getBody()).isEqualTo(other);
    }

    @Test
    void updateValidatesTheBodyTheSameWayAsCreate() {
        Map<String, Object> created = create("MCIT-U6", NAME);

        assertValidation(put(created, "MCIT U6", NAME), "code");
        assertValidation(put(created, "MCIT-U6", "   "), "name");
        assertValidation(put(created, "MCIT-U6", "n".repeat(101)), "name");
        assertThat(call(HttpMethod.GET, "/machines/" + created.get("id"), admin(), null).getBody())
                .isEqualTo(created);
    }

    @Test
    void updateOfAnUnknownMachineIs404AndCreatesNothing() {
        ResponseEntity<Map> response = call(HttpMethod.PUT, "/machines/999999999", admin(),
                new MachineRequest("MCIT-U7", NAME));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(count("select count(*) from machines where code = 'MCIT-U7'")).isZero();
    }

    // --- Baja (REQ-13, REQ-14, REQ-15) -------------------------------------------------------------

    @Test
    void deleteOfAMachineWithoutPartsIs204() {
        Map<String, Object> created = create("MCIT-D1", NAME);

        ResponseEntity<Map> response = call(HttpMethod.DELETE, "/machines/" + created.get("id"), admin(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(HttpMethod.GET, "/machines/" + created.get("id"), admin(), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteOfAMachineWithPartsIs409WithTheCountAndKeepsTheMachineAndItsParts() {
        int partsBefore = count("select count(*) from parts");

        ResponseEntity<Map> response = call(HttpMethod.DELETE, "/machines/1", admin(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "MACHINE_HAS_PARTS");
        assertThat((String) response.getBody().get("message")).contains("ENV-01").contains("7 partes");
        assertThat(count("select count(*) from machines where id = 1")).isEqualTo(1);
        assertThat(count("select count(*) from parts")).isEqualTo(partsBefore);
    }

    @Test
    void deleteOfAnUnknownMachineIs404() {
        assertThat(call(HttpMethod.DELETE, "/machines/999999999", admin(), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- Permisos (REQ-31) -------------------------------------------------------------------------

    @Test
    void productionAndTechnicianGet403OnEveryWriteAndNothingChanges() {
        Map<String, Object> created = create("MCIT-P1", NAME);
        String url = "/machines/" + created.get("id");

        for (String token : List.of(production(), technician())) {
            assertForbidden(call(HttpMethod.POST, "/machines", token, new MachineRequest("MCIT-P2", NAME)));
            assertForbidden(call(HttpMethod.PUT, url, token, new MachineRequest("MCIT-P3", "Cambiada")));
            assertForbidden(call(HttpMethod.DELETE, url, token, null));
        }

        assertThat(count("select count(*) from machines where code = 'MCIT-P2'")).isZero();
        assertThat(call(HttpMethod.GET, url, admin(), null).getBody()).isEqualTo(created);
    }

    @Test
    void administratorAndTeamLeaderCanWrite() {
        Map<String, String> codeByToken = Map.of(admin(), "MCIT-W1", teamLeader(), "MCIT-W2");
        codeByToken.forEach((token, code) -> {
            Map<String, Object> created = call(HttpMethod.POST, "/machines", token,
                    new MachineRequest(code, NAME)).getBody();
            assertThat(created).containsEntry("code", code);
            assertThat(call(HttpMethod.PUT, "/machines/" + created.get("id"), token,
                    new MachineRequest(code, "Editada")).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(call(HttpMethod.DELETE, "/machines/" + created.get("id"), token, null).getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
        });
    }

    // --- Orden de los errores (REQ-36) -------------------------------------------------------------

    @Test
    void theForbiddenWinsOverAMalformedIdAndOverAnInvalidBody() {
        assertForbidden(call(HttpMethod.PUT, "/machines/abc", production(), new MachineRequest("", "")));
        assertForbidden(call(HttpMethod.DELETE, "/machines/abc", technician(), null));
        assertForbidden(call(HttpMethod.POST, "/machines", technician(), new MachineRequest("", "")));
    }

    @Test
    void anInvalidBodyWinsOverAnUnknownMachine() {
        assertValidation(call(HttpMethod.PUT, "/machines/999999999", admin(), new MachineRequest("", "")),
                "code", "name");
    }

    @Test
    void anUnknownMachineWinsOverADuplicateCode() {
        ResponseEntity<Map> response = call(HttpMethod.PUT, "/machines/999999999", admin(),
                new MachineRequest("ENV-01", NAME));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- Ayudas ------------------------------------------------------------------------------------

    private Map<String, Object> create(String code, String name) {
        ResponseEntity<Map> response = call(HttpMethod.POST, "/machines", admin(), new MachineRequest(code, name));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<Map> put(Map<String, Object> machine, String code, String name) {
        return call(HttpMethod.PUT, "/machines/" + machine.get("id"), admin(), new MachineRequest(code, name));
    }

    private static Map<String, Object> machine(List<Map<String, Object>> machines, String code) {
        return machines.stream().filter(m -> code.equals(m.get("code"))).findFirst().orElseThrow();
    }

    private void assertValidation(ResponseEntity<Map> response, String... keys) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
        assertThat((Map) response.getBody().get("details")).containsOnlyKeys((Object[]) keys);
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

    private String admin() {
        return login("admin", "admin123");
    }

    private String teamLeader() {
        return login("teamleader", "teamleader123");
    }

    private String production() {
        return login("produccion", "produccion123");
    }

    private String technician() {
        return login("tecnico", "tecnico123");
    }

    private String login(String username, String password) {
        return tokens.computeIfAbsent(username, user -> restTemplate.postForEntity("/auth/login",
                new LoginRequest(user, password), LoginResponse.class).getBody().token());
    }
}
