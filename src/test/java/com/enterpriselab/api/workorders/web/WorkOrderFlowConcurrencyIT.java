package com.enterpriselab.api.workorders.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
 * REQ-7, REQ-20 y REQ-21 contra el Postgres real: las operaciones salen de hilos
 * distintos, liberadas a la vez por un {@link CountDownLatch}. Las órdenes llevan el
 * prefijo {@code WOFCIT-} y se limpian al terminar.
 */
@ActiveProfiles("dev")
@SuppressWarnings({"rawtypes", "unchecked"})
class WorkOrderFlowConcurrencyIT extends AbstractPostgresIT {

    private static final int REPETITIONS = 20;
    private static final String COMMENT_A = "Cierre A: se reemplazó el rodamiento y se verificó el funcionamiento.";
    private static final String COMMENT_B = "Cierre B: se ajustó la correa y se verificó el funcionamiento.";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, String> tokens = new HashMap<>();
    private ExecutorService pool;
    private long otherId;

    @BeforeEach
    void setUp() {
        otherId = SecondGuardiaTechnician.create(jdbcTemplate);
        pool = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void cleanUp() {
        pool.shutdownNow();
        jdbcTemplate.update("delete from work_orders where title like 'WOFCIT-%'");
        SecondGuardiaTechnician.delete(jdbcTemplate);
    }

    @Test
    void twoTakesOfTheSameOrderHaveExactlyOneWinnerWhoseOwnerTheLoserSees() throws Exception {
        String tecnico = login("tecnico", "tecnico123");
        String other = login(SecondGuardiaTechnician.USERNAME, SecondGuardiaTechnician.PASSWORD);
        long tecnicoId = userId("tecnico");

        for (int i = 0; i < REPETITIONS; i++) {
            String id = createPending();

            List<ResponseEntity<Map>> results = race(
                    () -> post(id, "take", tecnico, null),
                    () -> post(id, "take", other, null));

            ResponseEntity<Map> winner = single(results, HttpStatus.OK);
            ResponseEntity<Map> loser = single(results, HttpStatus.CONFLICT);
            assertThat(loser.getBody()).containsEntry("code", "WORK_ORDER_NOT_PENDING");
            String winnerId = (String) ((Map) winner.getBody().get("takenBy")).get("id");
            assertThat(((Map) loser.getBody().get("details")).get("takenById")).isEqualTo(winnerId);
            assertThat(winnerId).isIn(String.valueOf(tecnicoId), String.valueOf(otherId));
            assertThat(ownerOf(id)).isEqualTo(Long.valueOf(winnerId));
        }
    }

    @Test
    void twoClosesOfTheSameTechnicianHaveOneWinnerAndTheStoredNoteIsTheWinners() throws Exception {
        String tecnico = login("tecnico", "tecnico123");

        for (int i = 0; i < REPETITIONS; i++) {
            String id = createPending();
            post(id, "take", tecnico, null);

            List<ResponseEntity<Map>> results = race(
                    () -> post(id, "close", tecnico, Map.of("outcome", "completed", "comment", COMMENT_A)),
                    () -> post(id, "close", tecnico, Map.of("outcome", "completed", "comment", COMMENT_B)));

            ResponseEntity<Map> winner = single(results, HttpStatus.OK);
            ResponseEntity<Map> loser = single(results, HttpStatus.CONFLICT);
            assertThat(loser.getBody()).containsEntry("code", "WORK_ORDER_NOT_IN_PROGRESS");
            String stored = jdbcTemplate.queryForObject("select closing_comment from work_orders where id = ?",
                    String.class, Long.valueOf(id));
            assertThat(stored).isEqualTo(((Map) winner.getBody().get("closingNote")).get("comment"));
        }
    }

    @Test
    void aCloseAgainstAReleaseNeverLeavesAnImpossibleStateAndHasOneWinner() throws Exception {
        String tecnico = login("tecnico", "tecnico123");
        String teamLeader = login("teamleader", "teamleader123");

        for (int i = 0; i < REPETITIONS; i++) {
            String id = createPending();
            post(id, "take", tecnico, null);

            List<ResponseEntity<Map>> results = race(
                    () -> post(id, "close", tecnico, Map.of("outcome", "completed", "comment", COMMENT_A)),
                    () -> post(id, "release", teamLeader, null));

            ResponseEntity<Map> close = results.get(0);
            ResponseEntity<Map> release = results.get(1);
            assertThat(List.of(close.getStatusCode(), release.getStatusCode()))
                    .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
            assertConsistent(id, close.getStatusCode() == HttpStatus.OK ? "completed" : "pending");
        }
    }

    /** El cierre llega después de la liberación, incluso con otro técnico que ya retomó la orden. */
    @Test
    void aLateCloseOfThePreviousOwnerNeverAppliesOverAReleasedOrRetakenOrder() throws Exception {
        String tecnico = login("tecnico", "tecnico123");
        String other = login(SecondGuardiaTechnician.USERNAME, SecondGuardiaTechnician.PASSWORD);
        String teamLeader = login("teamleader", "teamleader123");

        for (int i = 0; i < REPETITIONS; i++) {
            String id = createPending();
            post(id, "take", tecnico, null);
            post(id, "release", teamLeader, null);

            ResponseEntity<Map> afterRelease = post(id, "close", tecnico,
                    Map.of("outcome", "completed", "comment", COMMENT_A));
            assertThat(afterRelease.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(afterRelease.getBody()).containsEntry("code", "WORK_ORDER_NOT_IN_PROGRESS");
            assertConsistent(id, "pending");

            post(id, "take", other, null);
            ResponseEntity<Map> afterRetake = post(id, "close", tecnico,
                    Map.of("outcome", "completed", "comment", COMMENT_A));
            assertThat(afterRetake.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(afterRetake.getBody()).containsEntry("code", "WORK_ORDER_TAKEN_BY_OTHER");
            assertConsistent(id, "in-progress");
            assertThat(ownerOf(id)).isEqualTo(otherId);
        }
    }

    // --- Ayudas ----------------------------------------------------------------------------------------

    /** Dispara las dos llamadas a la vez y devuelve sus respuestas en el mismo orden. */
    private List<ResponseEntity<Map>> race(Callable<ResponseEntity<Map>> first,
            Callable<ResponseEntity<Map>> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
        for (Callable<ResponseEntity<Map>> call : List.of(first, second)) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return call.call();
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        List<ResponseEntity<Map>> results = new ArrayList<>();
        for (Future<ResponseEntity<Map>> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        return results;
    }

    private static ResponseEntity<Map> single(List<ResponseEntity<Map>> results, HttpStatus status) {
        List<ResponseEntity<Map>> matching = results.stream().filter(r -> r.getStatusCode() == status).toList();
        assertThat(matching).as("respuestas %s entre %s", status, results.stream().map(r -> r.getStatusCode())
                .toList()).hasSize(1);
        return matching.get(0);
    }

    /** Estado final coherente: pending sin dueño ni nota, o completed con dueño y nota del mismo autor. */
    private void assertConsistent(String id, String expectedStatus) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select status, taken_by_id, closing_author_id, closing_comment from work_orders where id = ?",
                Long.valueOf(id));
        assertThat(row.get("status")).isEqualTo(expectedStatus);
        switch (expectedStatus) {
            case "pending" -> assertThat(row.get("taken_by_id")).isNull();
            case "in-progress" -> assertThat(row.get("taken_by_id")).isNotNull();
            default -> assertThat(row.get("closing_author_id")).isEqualTo(row.get("taken_by_id"));
        }
        if (!expectedStatus.equals("completed")) {
            assertThat(row.get("closing_author_id")).isNull();
            assertThat(row.get("closing_comment")).isNull();
        }
    }

    private String createPending() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "WOFCIT-" + System.nanoTime());
        body.put("description", "Descripción válida del problema");
        body.put("type", "pronto-intervencion");
        body.put("priority", "medium");
        body.put("machineRef", Map.of("machineId", "1"));
        ResponseEntity<Map> response = call(HttpMethod.POST, "/work-orders", login("produccion", "produccion123"),
                body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    private ResponseEntity<Map> post(String id, String verb, String token, Object body) {
        return call(HttpMethod.POST, "/work-orders/" + id + "/" + verb, token, body);
    }

    private ResponseEntity<Map> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private Long ownerOf(String id) {
        return jdbcTemplate.queryForObject("select taken_by_id from work_orders where id = ?", Long.class,
                Long.valueOf(id));
    }

    private long userId(String username) {
        return jdbcTemplate.queryForObject("select id from users where username = ?", Long.class, username);
    }

    private synchronized String login(String username, String password) {
        return tokens.computeIfAbsent(username, user -> restTemplate.postForEntity("/auth/login",
                new LoginRequest(user, password), LoginResponse.class).getBody().token());
    }
}
