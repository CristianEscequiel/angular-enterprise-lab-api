package com.enterpriselab.api.machines.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * REQ-16 a REQ-32, REQ-36 y REQ-37 sobre HTTP real. Las máquinas de prueba
 * llevan el prefijo {@code PCIT-} en el código y se limpian al terminar (la base
 * se comparte con el resto de las IT); el árbol del seed solo se lee.
 *
 * <p>Los cuerpos se mandan como {@code Map} para poder distinguir un campo
 * ausente de uno enviado como {@code null} (REQ-25).
 */
@ActiveProfiles("dev")
@SuppressWarnings({"rawtypes", "unchecked"})
class PartControllerIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, String> tokens = new HashMap<>();
    private String machineId;
    private String otherMachineId;

    @BeforeEach
    void createMachines() {
        machineId = String.valueOf(jdbcTemplate.queryForObject(
                "insert into machines (code, name) values ('PCIT-1', 'PartControllerIT') returning id", Long.class));
        otherMachineId = String.valueOf(jdbcTemplate.queryForObject(
                "insert into machines (code, name) values ('PCIT-2', 'PartControllerIT') returning id", Long.class));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from parts where machine_id in (select id from machines where code like 'PCIT-%')");
        jdbcTemplate.update("delete from machines where code like 'PCIT-%'");
    }

    // --- Lectura (REQ-16, REQ-17, REQ-30, REQ-32) ---------------------------------------------------

    @Test
    void listReturnsTheSeededEnvasadoraTreeAsAFlatListInOrderOfCreation() {
        ResponseEntity<List> response = call(HttpMethod.GET, "/machines/1/parts", admin(), null, List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> parts = response.getBody();
        assertThat(parts).allSatisfy(p -> assertThat(p).containsOnlyKeys("id", "machineId", "parentId", "name"));
        assertThat(parts).extracting(p -> p.get("id")).containsExactly("1", "2", "3", "4", "5", "6", "7");
        assertThat(parts).extracting(p -> p.get("machineId")).containsOnly("1");
        assertThat(parts).extracting(p -> p.get("parentId"))
                .containsExactly(null, "1", "2", "3", "1", null, "6");
        assertThat(parts.get(3)).containsEntry("name", "Rodamiento delantero");
    }

    @Test
    void aMachineWithoutPartsReturnsAnEmptyList() {
        ResponseEntity<List> response = call(HttpMethod.GET, "/machines/3/parts", admin(), null, List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void anUnknownOrNonNumericMachineIs404OnList() {
        for (String id : new String[] {"999999999", "abc"}) {
            ResponseEntity<Map> response = call(HttpMethod.GET, "/machines/" + id + "/parts", admin(), null);

            assertThat(response.getStatusCode()).as(id).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).containsEntry("code", "NOT_FOUND");
        }
    }

    @Test
    void everyRoleCanList() {
        for (String token : List.of(admin(), teamLeader(), production(), technician())) {
            assertThat(call(HttpMethod.GET, "/machines/1/parts", token, null, List.class).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void aFiveLevelTreeComesBackWithItsOriginalParentsAndCanBeRebuilt() {
        List<String> ids = new ArrayList<>();
        String parent = null;
        for (int level = 1; level <= 5; level++) {
            Map<String, Object> created = create(machineId, "Nivel " + level, parent);
            parent = (String) created.get("id");
            ids.add(parent);
        }
        create(machineId, "Hoja hermana del nivel 3", ids.get(1));

        List<Map<String, Object>> parts = call(HttpMethod.GET, "/machines/" + machineId + "/parts", admin(), null,
                List.class).getBody();

        assertThat(parts).hasSize(6);
        Map<String, String> parentOf = new LinkedHashMap<>();
        parts.forEach(p -> parentOf.put((String) p.get("id"), (String) p.get("parentId")));
        for (int level = 1; level < 5; level++) {
            assertThat(parentOf.get(ids.get(level))).isEqualTo(ids.get(level - 1));
        }
        assertThat(parentOf.get(ids.get(0))).isNull();
        assertThat(depth(ids.get(4), parentOf)).isEqualTo(5);
    }

    // --- Alta (REQ-18 a REQ-23, REQ-37) --------------------------------------------------------------

    @Test
    void createATopLevelPartWithAnAbsentOrNullParent() {
        ResponseEntity<Map> absent = post(machineId, Map.of("name", "Sin parentId"));
        Map<String, Object> explicitNull = new HashMap<>();
        explicitNull.put("name", "Con parentId nulo");
        explicitNull.put("parentId", null);
        ResponseEntity<Map> nulled = post(machineId, explicitNull);

        for (ResponseEntity<Map> response : List.of(absent, nulled)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).containsEntry("machineId", machineId).containsEntry("parentId", null);
            String id = (String) response.getBody().get("id");
            assertThat(id).matches("[0-9]+");
            assertThat(response.getHeaders().getLocation()).hasToString("/parts/" + id);
        }
    }

    @Test
    void createASubPartOfAPartOfTheSameMachine() {
        Map<String, Object> root = create(machineId, "Raíz", null);

        ResponseEntity<Map> response = post(machineId, Map.of("name", "Hija", "parentId", root.get("id")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("parentId", root.get("id")).containsEntry("name", "Hija");
    }

    @Test
    void createInAnUnknownMachineIs404AndCreatesNothing() {
        int before = count("select count(*) from parts");

        ResponseEntity<Map> response = post("999999999", Map.of("name", "X"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(count("select count(*) from parts")).isEqualTo(before);
    }

    @Test
    void aParentThatDoesNotExistIs400ParentPartNotFound() {
        for (String parentId : new String[] {"999999999", "abc", ""}) {
            ResponseEntity<Map> response = post(machineId, Map.of("name", "Huérfana", "parentId", parentId));

            assertThat(response.getStatusCode()).as("'%s'", parentId).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("code", "PARENT_PART_NOT_FOUND");
        }
        assertThat(count("select count(*) from parts where machine_id = ?", Long.valueOf(machineId))).isZero();
    }

    @Test
    void aParentOfAnotherMachineIs400ParentPartOtherMachine() {
        Map<String, Object> foreign = create(otherMachineId, "De la otra", null);

        ResponseEntity<Map> response = post(machineId, Map.of("name", "Cruzada", "parentId", foreign.get("id")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "PARENT_PART_OTHER_MACHINE");
        assertThat(count("select count(*) from parts where machine_id = ?", Long.valueOf(machineId))).isZero();
    }

    @Test
    void aBlankOrMissingNameIs400OnCreate() {
        Map<String, Object> missing = new HashMap<>();
        missing.put("parentId", null);
        for (Map<String, Object> body : List.<Map<String, Object>>of(missing, Map.of("name", ""), Map.of("name", "   "))) {
            assertValidation(post(machineId, body), "name");
        }
        assertThat(count("select count(*) from parts where machine_id = ?", Long.valueOf(machineId))).isZero();
    }

    @Test
    void theNameIsSavedTrimmedAndCappedAt100Characters() {
        ResponseEntity<Map> trimmed = post(machineId, Map.of("name", "  Recortada  "));
        ResponseEntity<Map> exactly100 = post(machineId, Map.of("name", " " + "n".repeat(100) + " "));
        ResponseEntity<Map> tooLong = post(machineId, Map.of("name", "n".repeat(101)));

        assertThat(trimmed.getBody()).containsEntry("name", "Recortada");
        assertThat(exactly100.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertValidation(tooLong, "name");
    }

    // --- Edición del nombre (REQ-24, REQ-25, REQ-26) -------------------------------------------------

    @Test
    void patchChangesOnlyTheNameAndKeepsMachineParentAndPosition() {
        Map<String, Object> root = create(machineId, "Raíz", null);
        Map<String, Object> child = create(machineId, "Hija", (String) root.get("id"));

        ResponseEntity<Map> response = patch((String) child.get("id"), Map.of("name", "  Renombrada "));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("id", child.get("id")).containsEntry("name", "Renombrada")
                .containsEntry("machineId", machineId).containsEntry("parentId", root.get("id"));
        assertThat(call(HttpMethod.GET, "/machines/" + machineId + "/parts", admin(), null, List.class).getBody())
                .extracting(p -> ((Map) p).get("name")).containsExactly("Raíz", "Renombrada");
    }

    @Test
    void patchAcceptsTheCurrentMachineAndParentWhenSent() {
        Map<String, Object> root = create(machineId, "Raíz", null);
        Map<String, Object> child = create(machineId, "Hija", (String) root.get("id"));

        ResponseEntity<Map> response = patch((String) child.get("id"),
                Map.of("name", "Igual", "machineId", machineId, "parentId", root.get("id")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void patchWithADifferentMachineOrParentIs400AndThePartIsUntouched() {
        Map<String, Object> root = create(machineId, "Raíz", null);
        Map<String, Object> other = create(otherMachineId, "Ajena", null);
        Map<String, Object> child = create(machineId, "Hija", (String) root.get("id"));
        String id = (String) child.get("id");

        assertValidation(patch(id, Map.of("name", "X", "machineId", otherMachineId)), "machineId");
        assertValidation(patch(id, Map.of("name", "X", "parentId", other.get("id"))), "parentId");
        Map<String, Object> toTopLevel = new HashMap<>();
        toTopLevel.put("name", "X");
        toTopLevel.put("parentId", null);
        assertValidation(patch(id, toTopLevel), "parentId");

        assertThat(part(id)).isEqualTo(child);
    }

    @Test
    void anExplicitNullParentOnATopLevelPartIsNotAMove() {
        Map<String, Object> root = create(machineId, "Raíz", null);
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Renombrada");
        body.put("parentId", null);

        assertThat(patch((String) root.get("id"), body).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void patchValidatesTheName() {
        Map<String, Object> root = create(machineId, "Raíz", null);
        String id = (String) root.get("id");
        Map<String, Object> missing = new HashMap<>();
        missing.put("machineId", machineId);

        assertValidation(patch(id, missing), "name");
        assertValidation(patch(id, Map.of("name", "  ")), "name");
        assertValidation(patch(id, Map.of("name", "n".repeat(101))), "name");
        assertThat(part(id)).isEqualTo(root);
    }

    @Test
    void patchOfAnUnknownOrNonNumericPartIs404() {
        for (String id : new String[] {"999999999", "abc"}) {
            ResponseEntity<Map> response = patch(id, Map.of("name", "X"));

            assertThat(response.getStatusCode()).as(id).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // --- Baja (REQ-27, REQ-28, REQ-29) ---------------------------------------------------------------

    @Test
    void deleteOfALeafIs204() {
        Map<String, Object> root = create(machineId, "Raíz", null);

        ResponseEntity<Map> response = delete((String) root.get("id"), admin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(count("select count(*) from parts where id = ?", Long.valueOf((String) root.get("id")))).isZero();
    }

    @Test
    void deleteOfAPartWithChildrenIs409WithTheCountAndKeepsTheWholeSubtree() {
        Map<String, Object> root = create(machineId, "Raíz", null);
        create(machineId, "Hija 1", (String) root.get("id"));
        create(machineId, "Hija 2", (String) root.get("id"));

        ResponseEntity<Map> response = delete((String) root.get("id"), teamLeader());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "PART_HAS_CHILDREN");
        assertThat((String) response.getBody().get("message")).contains("Raíz").contains("2 sub-partes");
        assertThat(count("select count(*) from parts where machine_id = ?", Long.valueOf(machineId))).isEqualTo(3);
    }

    @Test
    void aSubtreeIsDeletedFromTheLeavesUp() {
        Map<String, Object> root = create(machineId, "Raíz", null);
        Map<String, Object> child = create(machineId, "Hija", (String) root.get("id"));
        Map<String, Object> leaf = create(machineId, "Hoja", (String) child.get("id"));

        assertThat(delete((String) root.get("id"), admin()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(delete((String) leaf.get("id"), admin()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(delete((String) child.get("id"), admin()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(delete((String) root.get("id"), admin()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void deleteOfAnUnknownOrNonNumericPartIs404() {
        assertThat(delete("999999999", admin()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete("abc", admin()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- Permisos (REQ-31, REQ-32) --------------------------------------------------------------------

    @Test
    void productionAndTechnicianGet403OnEveryWriteAndNothingChanges() {
        Map<String, Object> root = create(machineId, "Raíz", null);
        String id = (String) root.get("id");

        for (String token : List.of(production(), technician())) {
            assertForbidden(call(HttpMethod.POST, "/machines/" + machineId + "/parts", token,
                    Map.of("name", "Nueva"), Map.class));
            assertForbidden(call(HttpMethod.PATCH, "/parts/" + id, token, Map.of("name", "Cambiada"), Map.class));
            assertForbidden(delete(id, token));
        }

        assertThat(count("select count(*) from parts where machine_id = ?", Long.valueOf(machineId))).isEqualTo(1);
        assertThat(part(id)).isEqualTo(root);
    }

    @Test
    void administratorAndTeamLeaderCanWrite() {
        for (String token : List.of(admin(), teamLeader())) {
            ResponseEntity<Map> created = call(HttpMethod.POST, "/machines/" + machineId + "/parts", token,
                    Map.of("name", "Escrita"), Map.class);
            String id = (String) created.getBody().get("id");

            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(call(HttpMethod.PATCH, "/parts/" + id, token, Map.of("name", "Editada"), Map.class)
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(delete(id, token).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    // --- Orden de los errores (REQ-36) ----------------------------------------------------------------

    @Test
    void theForbiddenWinsOverAMalformedIdAndOverAnInvalidBody() {
        assertForbidden(call(HttpMethod.POST, "/machines/abc/parts", production(), Map.of("name", ""), Map.class));
        assertForbidden(call(HttpMethod.PATCH, "/parts/abc", technician(), Map.of("name", ""), Map.class));
        assertForbidden(delete("abc", production()));
    }

    @Test
    void anInvalidNameWinsOverAnUnknownMachineOrPart() {
        assertValidation(post("999999999", Map.of("name", "  ", "parentId", "zzz")), "name");
        assertValidation(patch("999999999", Map.of("name", "  ")), "name");
    }

    @Test
    void anUnknownMachineWinsOverABadParent() {
        ResponseEntity<Map> response = post("999999999", Map.of("name", "X", "parentId", "zzz"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anUnknownPartWinsOverTheMoveCheck() {
        ResponseEntity<Map> response = patch("999999999",
                Map.of("name", "X", "machineId", otherMachineId, "parentId", "1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aBadParentWinsOverNothingElseOnAnExistingMachine() {
        ResponseEntity<Map> response = post(machineId, Map.of("name", "X", "parentId", "zzz"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "PARENT_PART_NOT_FOUND");
    }

    // --- CORS -----------------------------------------------------------------------------------------

    @Test
    void thePreflightOfPatchIsAllowedFromTheFrontendOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:4200");
        headers.setAccessControlRequestMethod(HttpMethod.PATCH);
        headers.setAccessControlRequestHeaders(List.of("Authorization", "Content-Type"));

        ResponseEntity<Void> response = restTemplate.exchange("/parts/1", HttpMethod.OPTIONS,
                new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:4200");
        assertThat(response.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.PATCH);
    }

    // --- Ayudas ---------------------------------------------------------------------------------------

    private Map<String, Object> create(String machine, String name, String parentId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("parentId", parentId);
        ResponseEntity<Map> response = post(machine, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<Map> post(String machine, Map<String, Object> body) {
        return call(HttpMethod.POST, "/machines/" + machine + "/parts", admin(), body, Map.class);
    }

    private ResponseEntity<Map> patch(String id, Map<String, Object> body) {
        return call(HttpMethod.PATCH, "/parts/" + id, admin(), body, Map.class);
    }

    private ResponseEntity<Map> delete(String id, String token) {
        return call(HttpMethod.DELETE, "/parts/" + id, token, null, Map.class);
    }

    /** La parte tal como la devuelve el listado de su máquina. */
    private Map<String, Object> part(String id) {
        List<Map<String, Object>> parts = call(HttpMethod.GET, "/machines/" + machineId + "/parts", admin(), null,
                List.class).getBody();
        return parts.stream().filter(p -> id.equals(p.get("id"))).findFirst().orElseThrow();
    }

    private static int depth(String id, Map<String, String> parentOf) {
        int depth = 0;
        for (String current = id; current != null; current = parentOf.get(current)) {
            depth++;
        }
        return depth;
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
        return call(method, path, token, body, Map.class);
    }

    private <T> ResponseEntity<T> call(HttpMethod method, String path, String token, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), type);
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
