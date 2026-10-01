package com.enterpriselab.api.dashboard.web;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * Spec 05 sobre HTTP real (REQ-1, 5, 6, 8, 10, 14, 15, 18 y 19), con los usuarios y las 32
 * órdenes del seed de dev. El contenedor se comparte con el resto de las IT, así que no se
 * afirma sobre totales absolutos sino sobre diferencias contra la propia consulta previa.
 * Las órdenes de prueba llevan el prefijo {@code DSHIT-} y se limpian al terminar; las del
 * seed solo se leen. Las cifras exactas están en {@code DashboardStatisticsAdapterIT}.
 */
@ActiveProfiles("dev")
@SuppressWarnings({"rawtypes", "unchecked"})
class DashboardControllerIT extends AbstractPostgresIT {

    private static final String COMMENT = "Se reemplazó el rodamiento y se verificó el funcionamiento.";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, String> tokens = new HashMap<>();

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from work_orders where title like 'DSHIT-%'");
    }

    // --- Resumen: forma y permisos (REQ-1, REQ-18) -----------------------------------------------------

    @Test
    void everyRoleGetsTheSummaryWithTheFullShape() {
        for (String token : List.of(admin(), teamLeader(), production(), tecnico())) {
            ResponseEntity<Map> response = get("/dashboard/summary", token);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            Map<String, Object> body = response.getBody();
            assertThat(body).containsOnlyKeys("period", "byStatus", "byPriority", "byType", "total", "open",
                    "closedInPeriod", "averageResolutionMinutes");
            assertThat((Map) body.get("byStatus")).containsOnlyKeys("pending", "in-progress", "completed",
                    "cancelled");
            assertThat((Map) body.get("byPriority")).containsOnlyKeys("low", "medium", "high");
            assertThat((Map) body.get("byType")).containsOnlyKeys("preventivo", "correctivo",
                    "pronto-intervencion");
            assertThat((Map) body.get("closedInPeriod")).containsOnlyKeys("completed", "cancelled", "total");
            assertThat((Map) body.get("period")).containsOnlyKeys("from", "to");
        }
    }

    @Test
    void withoutATokenBothEndpointsAre401() {
        for (String path : List.of("/dashboard/summary", "/dashboard/workload", "/dashboard/summary?from=abc")) {
            ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), Map.class);

            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).containsEntry("code", "UNAUTHORIZED");
        }
    }

    // --- Conteos (REQ-5, REQ-6) ------------------------------------------------------------------------

    @Test
    void totalAndOpenAreConsistentWithTheBreakdowns() {
        Map<String, Object> body = get("/dashboard/summary", admin()).getBody();

        long total = number(body.get("total"));
        assertThat(total).isGreaterThanOrEqualTo(32);
        assertThat(sum((Map) body.get("byStatus"))).isEqualTo(total);
        assertThat(sum((Map) body.get("byPriority"))).isEqualTo(total);
        assertThat(sum((Map) body.get("byType"))).isEqualTo(total);
        Map<String, Object> byStatus = (Map) body.get("byStatus");
        assertThat(number(body.get("open"))).isEqualTo(number(byStatus.get("pending"))
                + number(byStatus.get("in-progress")));
    }

    @Test
    void everyStatusAndPriorityMatchesTheListTotal() {
        Map<String, Object> body = get("/dashboard/summary", admin()).getBody();

        for (String status : List.of("pending", "in-progress", "completed", "cancelled")) {
            assertThat(number(((Map) body.get("byStatus")).get(status))).as(status)
                    .isEqualTo(listTotal("status=" + status));
        }
        for (String priority : List.of("low", "medium", "high")) {
            assertThat(number(((Map) body.get("byPriority")).get(priority))).as(priority)
                    .isEqualTo(listTotal("priority=" + priority));
        }
    }

    // --- Período (REQ-8, REQ-9, REQ-10) ----------------------------------------------------------------

    @Test
    void withoutParametersThePeriodIsThe30DaysEndingToday() {
        Map<String, Object> period = (Map) get("/dashboard/summary", admin()).getBody().get("period");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThat(LocalDate.parse((String) period.get("to"))).isBetween(today.minusDays(1), today);
        assertThat(LocalDate.parse((String) period.get("from")))
                .isEqualTo(LocalDate.parse((String) period.get("to")).minusDays(29));
    }

    @Test
    void anExplicitPeriodIsEchoedAndOneEndIsCompleted() {
        Map<String, Object> both = (Map) get("/dashboard/summary?from=2026-01-01&to=2026-01-31", admin())
                .getBody().get("period");
        Map<String, Object> onlyTo = (Map) get("/dashboard/summary?to=2026-03-31", admin()).getBody().get("period");

        assertThat(both).containsEntry("from", "2026-01-01").containsEntry("to", "2026-01-31");
        assertThat(onlyTo).containsEntry("from", "2026-03-02").containsEntry("to", "2026-03-31");
    }

    @Test
    void anInvalidPeriodIs400WithTheParameter() {
        for (String query : List.of("from=2026-9-1", "from=2026-02-30", "to=abc", "from=", "to=")) {
            ResponseEntity<Map> response = get("/dashboard/summary?" + query, admin());

            assertThat(response.getStatusCode()).as(query).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("code", "VALIDATION_ERROR");
            assertThat((Map) response.getBody().get("details")).isNotEmpty();
        }
        ResponseEntity<Map> reversed = get("/dashboard/summary?from=2026-09-10&to=2026-09-09", admin());
        assertThat(reversed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((Map) reversed.getBody().get("details")).containsOnlyKeys("from");
        ResponseEntity<Map> both = get("/dashboard/summary?from=x&to=y", tecnico());
        assertThat((Map) both.getBody().get("details")).containsOnlyKeys("from", "to");
    }

    // --- Transiciones y carga de trabajo (REQ-14, REQ-15) ----------------------------------------------

    @Test
    void everyTransitionShowsUpInTheNextSummary() {
        Map<String, Object> base = get("/dashboard/summary", admin()).getBody();

        String id = createPronto();
        Map<String, Object> created = get("/dashboard/summary", admin()).getBody();
        assertThat(delta(created, base, "total")).isEqualTo(1);
        assertThat(delta((Map) created.get("byStatus"), (Map) base.get("byStatus"), "pending")).isEqualTo(1);
        assertThat(delta((Map) created.get("byType"), (Map) base.get("byType"), "pronto-intervencion")).isEqualTo(1);
        assertThat(delta((Map) created.get("byPriority"), (Map) base.get("byPriority"), "medium")).isEqualTo(1);
        assertThat(delta(created, base, "open")).isEqualTo(1);

        post("/work-orders/" + id + "/take", tecnico(), null);
        Map<String, Object> taken = get("/dashboard/summary", admin()).getBody();
        assertThat(delta((Map) taken.get("byStatus"), (Map) created.get("byStatus"), "pending")).isEqualTo(-1);
        assertThat(delta((Map) taken.get("byStatus"), (Map) created.get("byStatus"), "in-progress")).isEqualTo(1);
        assertThat(delta(taken, created, "open")).isZero();

        post("/work-orders/" + id + "/release", admin(), null);
        Map<String, Object> released = get("/dashboard/summary", admin()).getBody();
        assertThat(delta((Map) released.get("byStatus"), (Map) taken.get("byStatus"), "pending")).isEqualTo(1);
        assertThat(delta((Map) released.get("byStatus"), (Map) taken.get("byStatus"), "in-progress")).isEqualTo(-1);

        post("/work-orders/" + id + "/take", tecnico(), null);
        post("/work-orders/" + id + "/close", tecnico(), Map.of("outcome", "completed", "comment", COMMENT));
        Map<String, Object> closed = get("/dashboard/summary", admin()).getBody();
        assertThat(delta((Map) closed.get("byStatus"), (Map) released.get("byStatus"), "completed")).isEqualTo(1);
        assertThat(delta((Map) closed.get("byStatus"), (Map) released.get("byStatus"), "pending")).isEqualTo(-1);
        assertThat(delta((Map) closed.get("closedInPeriod"), (Map) released.get("closedInPeriod"), "completed"))
                .isEqualTo(1);
        assertThat(delta((Map) closed.get("closedInPeriod"), (Map) released.get("closedInPeriod"), "total"))
                .isEqualTo(1);
        assertThat(closed.get("averageResolutionMinutes")).isNotNull();
        assertThat(((Number) closed.get("averageResolutionMinutes")).doubleValue()).isNotNegative();
    }

    @Test
    void anOrderClosedTodayDoesNotCountInAPastPeriod() {
        String id = createPronto();
        post("/work-orders/" + id + "/take", tecnico(), null);
        post("/work-orders/" + id + "/close", tecnico(), Map.of("outcome", "cancelled", "comment", COMMENT));

        Map<String, Object> past = (Map) get("/dashboard/summary?from=2020-01-01&to=2020-12-31", admin())
                .getBody().get("closedInPeriod");

        assertThat(number(past.get("total"))).isZero();
    }

    @Test
    void workloadListsTheTechnicianWithHisInProgressOrders() {
        long before = loadOf("Técnico Mecánico de Guardia");
        String first = createPronto();
        String second = createPronto();
        post("/work-orders/" + first + "/take", tecnico(), null);
        post("/work-orders/" + second + "/take", tecnico(), null);

        ResponseEntity<List> response = restTemplate.exchange("/dashboard/workload", HttpMethod.GET,
                new HttpEntity<>(headers(teamLeader())), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = response.getBody();
        assertThat(items).allSatisfy(item -> assertThat(item).containsOnlyKeys("takenById", "takenByName",
                "inProgress"));
        assertThat(loadOf("Técnico Mecánico de Guardia")).isEqualTo(before + 2);
        Long userId = jdbcTemplate.queryForObject("select id from users where username = 'tecnico'", Long.class);
        assertThat(items).anySatisfy(item -> assertThat(item.get("takenById")).isEqualTo(String.valueOf(userId)));
        List<Long> counts = items.stream().map(item -> number(item.get("inProgress"))).toList();
        assertThat(counts).isSortedAccordingTo((a, b) -> Long.compare(b, a));
    }

    @Test
    void workloadIsForAdministratorAndTeamLeaderOnly() {
        assertThat(statusOf("/dashboard/workload", admin())).isEqualTo(HttpStatus.OK);
        assertThat(statusOf("/dashboard/workload", teamLeader())).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> forbidden = get("/dashboard/workload", production());
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody()).containsEntry("code", "FORBIDDEN");
        assertThat(get("/dashboard/workload", tecnico()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- Ayudas ----------------------------------------------------------------------------------------

    private String createPronto() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "DSHIT-" + System.nanoTime());
        body.put("description", "Descripción válida del problema");
        body.put("type", "pronto-intervencion");
        body.put("priority", "medium");
        body.put("machineRef", Map.of("machineId", "1"));
        ResponseEntity<Map> response = post("/work-orders", production(), body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    private long loadOf(String name) {
        ResponseEntity<List> response = restTemplate.exchange("/dashboard/workload", HttpMethod.GET,
                new HttpEntity<>(headers(admin())), List.class);
        List<Map<String, Object>> items = response.getBody();
        return items.stream().filter(item -> name.equals(item.get("takenByName")))
                .mapToLong(item -> number(item.get("inProgress"))).sum();
    }

    private HttpStatus statusOf(String path, String token) {
        return HttpStatus.valueOf(restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)),
                Object.class).getStatusCode().value());
    }

    private long listTotal(String query) {
        return number(get("/work-orders?" + query, admin()).getBody().get("totalItems"));
    }

    private static long delta(Map<String, Object> after, Map<String, Object> before, String key) {
        return number(after.get(key)) - number(before.get(key));
    }

    private static long sum(Map<String, Object> counts) {
        return counts.values().stream().mapToLong(DashboardControllerIT::number).sum();
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private ResponseEntity<Map> get(String path, String token) {
        return call(HttpMethod.GET, path, token, null);
    }

    private ResponseEntity<Map> post(String path, String token, Object body) {
        return call(HttpMethod.POST, path, token, body);
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

    private String login(String username, String password) {
        return tokens.computeIfAbsent(username, user -> restTemplate.postForEntity("/auth/login",
                new LoginRequest(user, password), LoginResponse.class).getBody().token());
    }
}
