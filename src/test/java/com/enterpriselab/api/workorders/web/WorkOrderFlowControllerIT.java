package com.enterpriselab.api.workorders.web;

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
import com.enterpriselab.api.workorders.SecondGuardiaTechnician;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 04 sobre HTTP real: tomar, cerrar y liberar (REQ-1 a REQ-19 y REQ-22 a REQ-29).
 * Las órdenes de prueba llevan el prefijo {@code WOFIT-} y se crean por la API de la
 * spec 03; se limpian al terminar. Las 32 del seed solo se leen. Técnicos del seed:
 * {@code tecnico} (guardia) y {@code electricista} (preventivo-correctivo); el dueño
 * ajeno es {@link SecondGuardiaTechnician}.
 */
@ActiveProfiles("dev")
@SuppressWarnings({"rawtypes", "unchecked"})
class WorkOrderFlowControllerIT extends AbstractPostgresIT {

    private static final String PREFIX = "WOFIT-";
    private static final String DESCRIPTION = "Descripción válida del problema";
    private static final String COMMENT = "Se reemplazó el rodamiento y se verificó el funcionamiento.";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, String> tokens = new HashMap<>();
    private long otherGuardiaId;

    @BeforeEach
    void createSecondTechnician() {
        otherGuardiaId = SecondGuardiaTechnician.create(jdbcTemplate);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from work_orders where title like 'WOFIT-%'");
        SecondGuardiaTechnician.delete(jdbcTemplate);
        // El test que cambia el equipo del técnico lo restaura siempre acá.
        jdbcTemplate.update("update technicians set team_type = 'guardia' "
                + "where id = (select technician_id from users where username = 'tecnico')");
    }

    // =====================================================================================================
    // take
    // =====================================================================================================

    @Test
    void takeByAnEligibleTechnicianLeavesTheOrderInProgressInHisName() {
        String id = createPending("pronto-intervencion");
        long before = seededOrders();

        ResponseEntity<Map> response = take(id, tecnico());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("id")).isEqualTo(id);
        assertThat(body.get("status")).isEqualTo("in-progress");
        Map<String, Object> takenBy = (Map<String, Object>) body.get("takenBy");
        assertThat(takenBy.get("id")).isEqualTo(String.valueOf(userId("tecnico")));
        assertThat(takenBy.get("name")).isEqualTo("Técnico Mecánico de Guardia");
        assertThat(takenBy.get("at")).isNotNull();
        assertThat(body).doesNotContainKey("closingNote");
        assertThat(seededOrders()).isEqualTo(before);
    }

    @Test
    void aForeignTakenByInTheBodyIsIgnored() {
        String id = createPending("pronto-intervencion");

        ResponseEntity<Map> response = call(HttpMethod.POST, "/work-orders/" + id + "/take", tecnico(),
                Map.of("takenBy", Map.of("id", "1", "name", "Impostor", "at", "2020-01-01T00:00:00Z")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map) response.getBody().get("takenBy")).get("name")).isEqualTo("Técnico Mecánico de Guardia");
    }

    @Test
    void takeIsForbiddenAndLeavesTheOrderIntactForTheWrongTeamAndTheOtherRoles() {
        String preventivo = createPending("preventivo");
        String correctivo = createPending("correctivo");
        String pronto = createPending("pronto-intervencion");

        assertThat(take(preventivo, tecnico()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(take(correctivo, tecnico()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(take(pronto, electricista()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        for (String token : List.of(admin(), teamLeader(), production())) {
            assertThat(take(pronto, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(statusOf(preventivo)).isEqualTo("pending");
        assertThat(statusOf(correctivo)).isEqualTo("pending");
        assertThat(statusOf(pronto)).isEqualTo("pending");
        // y el equipo correcto sí puede
        assertThat(take(preventivo, electricista()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(take(correctivo, electricista()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void changingTheTechnicianTeamInTheMasterChangesTheAnswerWithoutANewToken() {
        String pronto = createPending("pronto-intervencion");
        String tecnicoToken = tecnico();
        jdbcTemplate.update("update technicians set team_type = 'preventivo-correctivo' "
                + "where id = (select technician_id from users where username = 'tecnico')");

        assertThat(take(pronto, tecnicoToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aTechnicianCanHoldSeveralOrdersInProgress() {
        String first = createPending("pronto-intervencion");
        String second = createPending("pronto-intervencion");

        assertThat(take(first, tecnico()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(take(second, tecnico()).getStatusCode()).isEqualTo(HttpStatus.OK);

        long me = userId("tecnico");
        assertThat(ownerOf(first)).isEqualTo(me);
        assertThat(ownerOf(second)).isEqualTo(me);
    }

    @Test
    void takeOfAnOrderThatIsNotPendingIsAConflictWithTheStateAndTheOwner() {
        String mine = createPending("pronto-intervencion");
        take(mine, tecnico());
        String theirs = createPending("pronto-intervencion");
        force(theirs, "in-progress", otherGuardiaId, null);
        String completed = createPending("pronto-intervencion");
        force(completed, "completed", otherGuardiaId, COMMENT);
        String cancelled = createPending("pronto-intervencion");
        force(cancelled, "cancelled", otherGuardiaId, COMMENT);

        for (String id : List.of(mine, theirs, completed, cancelled)) {
            String statusBefore = statusOf(id);
            ResponseEntity<Map> response = take(id, tecnico());

            assertConflict(response, "WORK_ORDER_NOT_PENDING", statusBefore);
            assertThat(((Map) response.getBody().get("details"))).containsKeys("takenById", "takenByName");
            assertThat(statusOf(id)).isEqualTo(statusBefore);
        }
        assertThat(ownerOf(theirs)).isEqualTo(otherGuardiaId);
    }

    @Test
    void anUnknownOrNonNumericIdIsNotFoundInTheThreeVerbs() {
        for (String id : List.of("999999999", "abc", "1.5")) {
            assertNotFound(take(id, tecnico()));
            assertNotFound(close(id, tecnico(), validClose()));
            assertNotFound(release(id, admin()));
        }
    }

    // =====================================================================================================
    // close
    // =====================================================================================================

    @Test
    void closeCompletedAndCancelledStoreTheTrimmedNoteAndKeepTheOwner() {
        String completed = createPending("pronto-intervencion");
        String cancelled = createPending("pronto-intervencion");
        Map<String, Object> taken = (Map<String, Object>) take(completed, tecnico()).getBody().get("takenBy");
        take(cancelled, tecnico());

        ResponseEntity<Map> done = close(completed, tecnico(), Map.of("outcome", "completed",
                "comment", "   " + COMMENT + "   "));
        ResponseEntity<Map> dropped = close(cancelled, tecnico(), Map.of("outcome", "cancelled",
                "comment", COMMENT));

        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(done.getBody().get("status")).isEqualTo("completed");
        assertThat(done.getBody().get("takenBy")).isEqualTo(taken);
        Map<String, Object> note = (Map<String, Object>) done.getBody().get("closingNote");
        assertThat(note.get("comment")).isEqualTo(COMMENT);
        assertThat(note.get("authorId")).isEqualTo(String.valueOf(userId("tecnico")));
        assertThat(note.get("authorName")).isEqualTo("Técnico Mecánico de Guardia");
        assertThat(note.get("at")).isNotNull();
        assertThat(dropped.getBody().get("status")).isEqualTo("cancelled");
    }

    @Test
    void theClosingAuthorComesFromTheTokenEvenIfTheBodyBringsAnother() {
        String id = createPending("pronto-intervencion");
        take(id, tecnico());

        ResponseEntity<Map> response = close(id, tecnico(), Map.of("outcome", "completed", "comment", COMMENT,
                "authorId", "1", "authorName", "Impostor"));

        Map<String, Object> note = (Map<String, Object>) response.getBody().get("closingNote");
        assertThat(note.get("authorName")).isEqualTo("Técnico Mecánico de Guardia");
        assertThat(note.get("authorId")).isEqualTo(String.valueOf(userId("tecnico")));
    }

    @Test
    void closeWithAnInvalidBodyIs400WithTheFieldAndTheOrderStaysInProgress() {
        String id = createPending("pronto-intervencion");
        take(id, tecnico());

        for (Map<String, Object> body : List.<Map<String, Object>>of(
                Map.of("comment", COMMENT),
                Map.of("outcome", "pending", "comment", COMMENT),
                Map.of("outcome", "Completed", "comment", COMMENT))) {
            assertValidation(close(id, tecnico(), body), "outcome");
        }
        for (String comment : List.of("", "   ", "corto", "a".repeat(49), "a".repeat(501))) {
            assertValidation(close(id, tecnico(), Map.of("outcome", "completed", "comment", comment)), "comment");
        }
        assertValidation(close(id, tecnico(), Map.of()), "outcome", "comment");
        assertThat(statusOf(id)).isEqualTo("in-progress");
        assertThat(closingCommentOf(id)).isNull();
    }

    @Test
    void closeBoundariesOf50And500CharactersAreAccepted() {
        String first = createPending("pronto-intervencion");
        String second = createPending("pronto-intervencion");
        take(first, tecnico());
        take(second, tecnico());

        assertThat(close(first, tecnico(), Map.of("outcome", "completed", "comment", "a".repeat(50)))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(close(second, tecnico(), Map.of("outcome", "completed", "comment", "a".repeat(500)))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void closeWithoutABodyIs403ForProductionAnd400ForATechnician() {
        String id = createPending("pronto-intervencion");
        take(id, tecnico());

        assertThat(call(HttpMethod.POST, "/work-orders/" + id + "/close", production(), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> technician = call(HttpMethod.POST, "/work-orders/" + id + "/close", tecnico(), null);
        assertValidation(technician, "outcome", "comment");
    }

    @Test
    void closeIsForbiddenForTheWrongRoleOrTeam() {
        String pronto = createPending("pronto-intervencion");
        take(pronto, tecnico());
        String preventivo = createPending("preventivo");
        take(preventivo, electricista());

        for (String token : List.of(admin(), teamLeader(), production())) {
            assertThat(close(pronto, token, validClose()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(close(preventivo, tecnico(), validClose()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(close(pronto, electricista(), validClose()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusOf(pronto)).isEqualTo("in-progress");
        assertThat(statusOf(preventivo)).isEqualTo("in-progress");
    }

    @Test
    void closeConflictsAreNotInProgressAndTakenByOtherWithTheirDetails() {
        String pending = createPending("pronto-intervencion");
        String theirs = createPending("pronto-intervencion");
        force(theirs, "in-progress", otherGuardiaId, null);
        String closedByOther = createPending("pronto-intervencion");
        force(closedByOther, "completed", otherGuardiaId, COMMENT);

        assertConflict(close(pending, tecnico(), validClose()), "WORK_ORDER_NOT_IN_PROGRESS", "pending");
        ResponseEntity<Map> other = close(theirs, tecnico(), validClose());
        assertConflict(other, "WORK_ORDER_TAKEN_BY_OTHER", "in-progress");
        assertThat((Map) other.getBody().get("details")).containsEntry("takenById", String.valueOf(otherGuardiaId))
                .containsEntry("takenByName", SecondGuardiaTechnician.DISPLAY_NAME);
        // cerrada por otro técnico: NOT_IN_PROGRESS, no TAKEN_BY_OTHER
        assertConflict(close(closedByOther, tecnico(), validClose()), "WORK_ORDER_NOT_IN_PROGRESS", "completed");
        assertThat(statusOf(theirs)).isEqualTo("in-progress");
        assertThat(ownerOf(theirs)).isEqualTo(otherGuardiaId);
    }

    // =====================================================================================================
    // release
    // =====================================================================================================

    @Test
    void administratorAndTeamLeaderReleaseAnOrderInProgress() {
        for (String token : List.of(admin(), teamLeader())) {
            String id = createPending("pronto-intervencion");
            take(id, tecnico());

            ResponseEntity<Map> response = release(id, token);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("status")).isEqualTo("pending");
            assertThat(response.getBody()).containsEntry("takenBy", null).doesNotContainKey("closingNote");
        }
    }

    @Test
    void releaseIsForbiddenForTechniciansEvenTheOwnerAndForProduction() {
        String id = createPending("pronto-intervencion");
        take(id, tecnico());

        assertThat(release(id, tecnico()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(release(id, production()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusOf(id)).isEqualTo("in-progress");
        // 403 gana al 409 sobre una orden en estado equivocado
        String pending = createPending("pronto-intervencion");
        assertThat(release(pending, tecnico()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void releaseConflictsOnPendingAndClosedOrders() {
        String pending = createPending("pronto-intervencion");
        String completed = createPending("pronto-intervencion");
        force(completed, "completed", otherGuardiaId, COMMENT);
        String cancelled = createPending("pronto-intervencion");
        force(cancelled, "cancelled", otherGuardiaId, COMMENT);

        assertConflict(release(pending, admin()), "WORK_ORDER_NOT_IN_PROGRESS", "pending");
        assertConflict(release(completed, admin()), "WORK_ORDER_NOT_IN_PROGRESS", "completed");
        assertConflict(release(cancelled, teamLeader()), "WORK_ORDER_NOT_IN_PROGRESS", "cancelled");
        assertThat(ownerOf(completed)).isEqualTo(otherGuardiaId);
        assertThat(closingCommentOf(completed)).isEqualTo(COMMENT);
    }

    @Test
    void aReleasedOrderCanBeTakenAgainByTheSameOrAnotherTechnician() {
        String id = createPending("pronto-intervencion");
        take(id, tecnico());
        release(id, admin());

        assertThat(take(id, tecnico()).getStatusCode()).isEqualTo(HttpStatus.OK);
        release(id, teamLeader());
        String otherToken = login(SecondGuardiaTechnician.USERNAME, SecondGuardiaTechnician.PASSWORD);
        ResponseEntity<Map> retaken = take(id, otherToken);

        assertThat(retaken.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map) retaken.getBody().get("takenBy")).get("id")).isEqualTo(String.valueOf(otherGuardiaId));
    }

    // =====================================================================================================
    // Órdenes cerradas, PUT, lecturas, orden de errores
    // =====================================================================================================

    @Test
    void closedOrdersRejectTakeCloseAndReleaseAndNothingChanges() {
        String id = createPending("pronto-intervencion");
        take(id, tecnico());
        close(id, tecnico(), validClose());
        Map<String, Object> before = get(id, admin()).getBody();

        assertConflict(take(id, tecnico()), "WORK_ORDER_NOT_PENDING", "completed");
        assertConflict(close(id, tecnico(), validClose()), "WORK_ORDER_NOT_IN_PROGRESS", "completed");
        assertConflict(release(id, admin()), "WORK_ORDER_NOT_IN_PROGRESS", "completed");

        assertThat(get(id, admin()).getBody()).isEqualTo(before);
    }

    @Test
    void aPutKeepsTheStatusOwnerAndNoteOfAnOrderThatWentThroughTake() {
        String id = createPending("pronto-intervencion");
        take(id, tecnico());
        Map<String, Object> taken = get(id, admin()).getBody();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", PREFIX + "editada");
        body.put("description", DESCRIPTION);
        body.put("priority", "high");
        body.put("status", "completed");
        body.put("takenBy", null);
        body.put("closingNote", Map.of("comment", "x"));
        ResponseEntity<Map> response = call(HttpMethod.PUT, "/work-orders/" + id, admin(), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("in-progress");
        assertThat(response.getBody().get("takenBy")).isEqualTo(taken.get("takenBy"));
        assertThat(response.getBody()).doesNotContainKey("closingNote");
    }

    @Test
    void everyTransitionIsReflectedByTheDetailTheListAndTheStatusFilter() {
        String id = createPending("pronto-intervencion");
        String title = (String) get(id, admin()).getBody().get("title");

        take(id, tecnico());
        assertThat(get(id, admin()).getBody().get("status")).isEqualTo("in-progress");
        assertThat(listed(title, "in-progress")).hasSize(1);
        assertThat(listed(title, "pending")).isEmpty();

        release(id, admin());
        assertThat(listed(title, "pending")).hasSize(1);

        take(id, tecnico());
        close(id, tecnico(), validClose());
        List<Map<String, Object>> completed = listed(title, "completed");
        assertThat(completed).hasSize(1);
        assertThat((Map) completed.get(0).get("closingNote")).containsEntry("comment", COMMENT);
        assertThat(completed.get(0).get("takenBy")).isNotNull();
    }

    @Test
    void errorOrderIsForbiddenThenBadRequestThenNotFoundThenTeamForbidden() {
        // 403 de rol sobre 400
        assertThat(close("999999999", production(), Map.of()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // 400 sobre 404
        assertValidation(close("999999999", tecnico(), Map.of("outcome", "x")), "outcome", "comment");
        // 404 sobre 403 de equipo
        assertNotFound(close("999999999", tecnico(), validClose()));
        assertNotFound(take("999999999", tecnico()));
        // 400 sobre 403 de equipo: guardia con un preventivo y un cuerpo inválido
        String preventivo = createPending("preventivo");
        assertValidation(close(preventivo, tecnico(), Map.of("outcome", "x")), "outcome", "comment");
    }

    @Test
    void withoutATokenEveryVerbIs401() {
        for (String verb : List.of("take", "close", "release")) {
            ResponseEntity<Map> response = restTemplate.exchange("/work-orders/1/" + verb, HttpMethod.POST,
                    new HttpEntity<>(new HttpHeaders()), Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).containsEntry("code", "UNAUTHORIZED");
        }
    }

    // =====================================================================================================
    // Ayudas
    // =====================================================================================================

    /** Crea una orden pendiente con el rol que puede crear ese tipo y le pone un título propio. */
    private String createPending(String type) {
        String creator = type.equals("pronto-intervencion") ? production() : teamLeader();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", PREFIX + type + "-" + System.nanoTime());
        body.put("description", DESCRIPTION);
        body.put("type", type);
        body.put("priority", "medium");
        body.put("machineRef", Map.of("machineId", "1"));
        ResponseEntity<Map> response = call(HttpMethod.POST, "/work-orders", creator, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    /** Estado, dueño y (si cerrada) nota por SQL: un dueño ajeno solo se puede fijar así. */
    private void force(String id, String status, long ownerId, String comment) {
        boolean closed = comment != null;
        jdbcTemplate.update("update work_orders set status = ?, taken_by_id = u.id, taken_by_name = u.display_name, "
                + "taken_at = now(), closing_comment = ?, closing_author_id = "
                + (closed ? "u.id" : "null") + ", closing_author_name = "
                + (closed ? "u.display_name" : "null") + ", closed_at = "
                + (closed ? "now()" : "null") + " from users u where u.id = ? and work_orders.id = ?",
                status, comment, ownerId, Long.valueOf(id));
    }

    private static Map<String, Object> validClose() {
        return Map.of("outcome", "completed", "comment", COMMENT);
    }

    private long seededOrders() {
        return jdbcTemplate.queryForObject("select count(*) from work_orders where id <= 32 and status = 'pending'",
                Long.class);
    }

    private String statusOf(String id) {
        return jdbcTemplate.queryForObject("select status from work_orders where id = ?", String.class,
                Long.valueOf(id));
    }

    private Long ownerOf(String id) {
        return jdbcTemplate.queryForObject("select taken_by_id from work_orders where id = ?", Long.class,
                Long.valueOf(id));
    }

    private String closingCommentOf(String id) {
        return jdbcTemplate.queryForObject("select closing_comment from work_orders where id = ?", String.class,
                Long.valueOf(id));
    }

    private long userId(String username) {
        return jdbcTemplate.queryForObject("select id from users where username = ?", Long.class, username);
    }

    private List<Map<String, Object>> listed(String title, String status) {
        ResponseEntity<Map> page = restTemplate.exchange("/work-orders?title={t}&status={s}", HttpMethod.GET,
                new HttpEntity<>(headers(admin())), Map.class, title, status);
        return (List<Map<String, Object>>) page.getBody().get("data");
    }

    private void assertConflict(ResponseEntity<Map> response, String code, String status) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", code);
        assertThat((Map) response.getBody().get("details")).containsEntry("status", status);
    }

    private void assertNotFound(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "NOT_FOUND");
    }

    private void assertValidation(ResponseEntity<Map> response, String... keys) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
        assertThat((Map<String, Object>) response.getBody().get("details")).containsOnlyKeys(keys);
    }

    private ResponseEntity<Map> take(String id, String token) {
        return call(HttpMethod.POST, "/work-orders/" + id + "/take", token, null);
    }

    private ResponseEntity<Map> close(String id, String token, Map<String, Object> body) {
        return call(HttpMethod.POST, "/work-orders/" + id + "/close", token, body);
    }

    private ResponseEntity<Map> release(String id, String token) {
        return call(HttpMethod.POST, "/work-orders/" + id + "/release", token, null);
    }

    private ResponseEntity<Map> get(String id, String token) {
        return call(HttpMethod.GET, "/work-orders/" + id, token, null);
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

    private String admin() {
        return login("admin", "admin123");
    }

    private String teamLeader() {
        return login("teamleader", "teamleader123");
    }

    private String production() {
        return login("produccion", "produccion123");
    }

    private String tecnico() {
        return login("tecnico", "tecnico123");
    }

    private String electricista() {
        return login("electricista", "electricista123");
    }

    private String login(String username, String password) {
        return tokens.computeIfAbsent(username, user -> restTemplate.postForEntity("/auth/login",
                new LoginRequest(user, password), LoginResponse.class).getBody().token());
    }
}
