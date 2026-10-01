package com.enterpriselab.api.workorders.web;

import java.time.Instant;
import java.util.ArrayList;
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
 * REQ-1 a REQ-42 y REQ-46 sobre HTTP real, con los usuarios, las máquinas, las partes y
 * las 32 órdenes del seed de dev. Las órdenes de prueba llevan el prefijo
 * {@code WOCIT-} en el título y las máquinas de prueba en el código, y se limpian al
 * terminar (la base se comparte con el resto de las IT); las órdenes del seed solo se
 * leen. Como esta spec no puede cambiar el estado por la API (es de la spec 04), los
 * estados {@code in-progress}, {@code completed} y {@code cancelled} se fuerzan por SQL
 * sobre órdenes propias.
 *
 * <p>Los cuerpos se mandan como {@code Map} para poder distinguir un campo ausente de
 * uno enviado como {@code null} (REQ-34).
 */
@ActiveProfiles("dev")
@SuppressWarnings({"rawtypes", "unchecked"})
class WorkOrderControllerIT extends AbstractPostgresIT {

    private static final String PREFIX = "WOCIT-";
    private static final String DESCRIPTION = "Descripción válida del problema";
    private static final String ENVASADORA = "Envasadora línea 1";
    private static final String MOTOR_CHAIN = ENVASADORA + " > Mesa de transporte > Cinta 1 > Motor de cinta";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, String> tokens = new HashMap<>();

    @AfterEach
    void cleanUp() {
        // 'abc' es el título mínimo que no puede llevar el prefijo: lo crea theLimitsOf...
        jdbcTemplate.update("delete from work_orders where title like 'WOCIT-%' or title = 'abc'");
        jdbcTemplate.update("delete from parts where machine_id in (select id from machines where code like 'WOCIT-%')");
        jdbcTemplate.update("delete from machines where code like 'WOCIT-%'");
    }

    // =====================================================================================================
    // Listado (REQ-1 a REQ-12)
    // =====================================================================================================

    @Test
    void listWithoutParametersReturnsTheFirstPageOfTenInIdOrder() {
        ResponseEntity<Map> response = get("/work-orders", admin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsOnlyKeys("data", "page", "size", "totalItems", "totalPages");
        assertThat(body.get("page")).isEqualTo(1);
        assertThat(body.get("size")).isEqualTo(10);
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        assertThat(data).hasSize(10);
        assertThat(data).extracting(o -> o.get("id")).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        assertThat(data).allSatisfy(o -> assertThat(o).containsKeys("id", "title", "description", "machineRef",
                "type", "priority", "status", "createdAt", "takenBy"));
        assertThat((Integer) body.get("totalItems")).isGreaterThanOrEqualTo(32);
        int total = (Integer) body.get("totalItems");
        assertThat(body.get("totalPages")).isEqualTo((total + 9) / 10);
    }

    @Test
    void pageAndSizeSelectTheRightOrdersAndTheTotalsFollow() {
        Map<String, Object> body = get("/work-orders?page=2&size=5", admin()).getBody();

        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        assertThat(data).extracting(o -> o.get("id")).containsExactly("6", "7", "8", "9", "10");
        assertThat(body.get("page")).isEqualTo(2);
        assertThat(body.get("size")).isEqualTo(5);
        int total = (Integer) body.get("totalItems");
        assertThat(body.get("totalPages")).isEqualTo((total + 4) / 5);
    }

    @Test
    void theIdOrderIsNumericAndNotTextual() {
        List<Map<String, Object>> data = (List<Map<String, Object>>) get("/work-orders?page=2&size=10", admin())
                .getBody().get("data");

        // Con orden de texto, "10" iría antes que "2": acá la página 2 empieza en 11.
        assertThat(data).extracting(o -> o.get("id")).startsWith("11", "12", "13");
    }

    @Test
    void aPageBeyondTheLastIsEmptyWithTheRealTotals() {
        Map<String, Object> body = get("/work-orders?page=9999&size=10", admin()).getBody();

        assertThat((List) body.get("data")).isEmpty();
        assertThat((Integer) body.get("totalItems")).isGreaterThanOrEqualTo(32);
        assertThat((Integer) body.get("totalPages")).isGreaterThanOrEqualTo(4);
        assertThat(body.get("page")).isEqualTo(9999);
    }

    @Test
    void noMatchesIsAnEmptyListWithZeroTotals() {
        Map<String, Object> body = get("/work-orders?title={t}", admin(), "WOCIT-nadie-se-llama-asi-zzz").getBody();

        assertThat((List) body.get("data")).isEmpty();
        assertThat(body.get("totalItems")).isEqualTo(0);
        assertThat(body.get("totalPages")).isEqualTo(0);
    }

    @Test
    void theTitleSearchIgnoresCaseAndTheSpacesAtTheEdges() {
        create("WOCIT-Motor Uno", "1", null);
        create("WOCIT-otra cosa", "1", null);

        assertThat(titles(get("/work-orders?title={t}", admin(), "wocit-motor"))).containsExactly("WOCIT-Motor Uno");
        assertThat(titles(get("/work-orders?title={t}", admin(), "  WOCIT-MOTOR UNO  ")))
                .containsExactly("WOCIT-Motor Uno");
    }

    @Test
    void percentAndUnderscoreInTheSearchAreLiteral() {
        create("WOCIT-100% listo", "1", null);
        create("WOCIT-1000 listo", "1", null);
        create("WOCIT-a_b", "1", null);
        create("WOCIT-axb", "1", null);

        assertThat(titles(get("/work-orders?title={t}", admin(), "100%"))).containsExactly("WOCIT-100% listo");
        assertThat(titles(get("/work-orders?title={t}", admin(), "wocit-a_b"))).containsExactly("WOCIT-a_b");
    }

    @Test
    void aBlankTitleDoesNotFilter() {
        int all = (Integer) get("/work-orders", admin()).getBody().get("totalItems");

        assertThat(get("/work-orders?title=", admin()).getBody().get("totalItems")).isEqualTo(all);
        assertThat(get("/work-orders?title={t}", admin(), "   ").getBody().get("totalItems")).isEqualTo(all);
    }

    @Test
    void theStatusAndPriorityFiltersReflectTheirOwnTotals() {
        Map<String, Object> cancelled = get("/work-orders?status=cancelled&size=100", admin()).getBody();
        Map<String, Object> high = get("/work-orders?priority=high&size=100", admin()).getBody();

        assertThat(cancelled.get("totalItems")).isEqualTo(count("select count(*) from work_orders where status = 'cancelled'"));
        assertThat((List<Map<String, Object>>) cancelled.get("data"))
                .allSatisfy(o -> assertThat(o.get("status")).isEqualTo("cancelled"));
        assertThat(high.get("totalItems")).isEqualTo(count("select count(*) from work_orders where priority = 'high'"));
        assertThat((List<Map<String, Object>>) high.get("data"))
                .allSatisfy(o -> assertThat(o.get("priority")).isEqualTo("high"));
        assertThat(cancelled.get("totalPages")).isEqualTo(1);
    }

    @Test
    void theThreeFiltersCombineWithAnd() {
        String a = create("WOCIT-f-a", "1", null).get("id").toString();
        String b = create("WOCIT-f-b", "1", null).get("id").toString();
        create("WOCIT-f-c", "1", null);
        force(a, "completed");
        force(b, "completed");
        jdbcTemplate.update("update work_orders set priority = 'high' where id = ?", Long.valueOf(b));

        ResponseEntity<Map> all = get("/work-orders?title=wocit-f-&status=completed&priority=high", admin());
        ResponseEntity<Map> none = get("/work-orders?title=wocit-f-&status=pending&priority=high", admin());

        assertThat(titles(all)).containsExactly("WOCIT-f-b");
        assertThat(all.getBody().get("totalItems")).isEqualTo(1);
        assertThat(all.getBody().get("totalPages")).isEqualTo(1);
        assertThat(titles(none)).isEmpty();
    }

    @Test
    void invalidParametersAre400WithTheParameterInTheDetails() {
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("page=0", "page");
        cases.put("page=-1", "page");
        cases.put("page=abc", "page");
        cases.put("page=1.5", "page");
        cases.put("page=99999999999", "page");
        cases.put("size=0", "size");
        cases.put("size=101", "size");
        cases.put("size=abc", "size");
        cases.put("size=1.5", "size");
        cases.put("status=open", "status");
        cases.put("status=Pending", "status");
        cases.put("priority=urgent", "priority");
        cases.put("priority=HIGH", "priority");

        cases.forEach((query, key) -> {
            ResponseEntity<Map> response = get("/work-orders?" + query, admin());

            assertThat(response.getStatusCode()).as(query).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).as(query).containsEntry("code", "VALIDATION_ERROR")
                    .containsEntry("path", "/work-orders");
            assertThat((Map) response.getBody().get("details")).as(query).containsOnlyKeys(key);
        });
    }

    @Test
    void everyParameterErrorIsReportedTogether() {
        ResponseEntity<Map> response = get("/work-orders?page=0&size=500&status=x&priority=y", admin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((Map) response.getBody().get("details")).containsOnlyKeys("page", "size", "status", "priority");
    }

    @Test
    void theLargestAllowedSizeIsAccepted() {
        assertThat(get("/work-orders?size=100", admin()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/work-orders?size=1", admin()).getBody().get("data")).asList().hasSize(1);
    }

    // =====================================================================================================
    // Consulta (REQ-13, REQ-14)
    // =====================================================================================================

    @Test
    void getReturnsTheSeededOrder3WithItsMachineRefOwnerAndClosingNote() {
        long electricista = count("select id from users where username = 'electricista'");

        ResponseEntity<Map> response = get("/work-orders/3", admin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> order = response.getBody();
        assertThat(order).containsEntry("id", "3").containsEntry("title", "Inspección de tablero eléctrico")
                .containsEntry("type", "preventivo").containsEntry("priority", "low")
                .containsEntry("status", "completed").containsEntry("createdAt", "2026-08-03T14:00:00Z");
        assertThat((Map) order.get("machineRef")).containsOnlyKeys("machineId", "partId", "breadcrumb", "comment")
                .containsEntry("machineId", "1").containsEntry("partId", "3")
                .containsEntry("breadcrumb", MOTOR_CHAIN).containsEntry("comment", "");
        assertThat((Map) order.get("takenBy")).containsOnlyKeys("id", "name", "at")
                .containsEntry("id", String.valueOf(electricista))
                .containsEntry("name", "Técnico Electricista Preventivo")
                .containsEntry("at", "2026-08-03T15:00:00Z");
        assertThat((Map) order.get("closingNote")).containsOnlyKeys("comment", "authorId", "authorName", "at")
                .containsEntry("authorId", String.valueOf(electricista))
                .containsEntry("authorName", "Técnico Electricista Preventivo")
                .containsEntry("at", "2026-08-03T20:00:00Z");
    }

    @Test
    void aPendingOrderHasANullOwnerAndNoClosingNoteAtAll() {
        Map<String, Object> pending = get("/work-orders/10", admin()).getBody();

        assertThat(pending).containsEntry("status", "pending").containsEntry("takenBy", null)
                .doesNotContainKey("closingNote");
    }

    @Test
    void anOrderOnTheWholeMachineHasANullPartIdInTheJson() {
        Map<String, Object> order = get("/work-orders/1", admin()).getBody();

        assertThat((Map) order.get("machineRef")).containsEntry("partId", null)
                .containsEntry("breadcrumb", ENVASADORA);
    }

    @Test
    void anUnknownOrNonNumericIdIs404WithTheApiError() {
        for (String id : new String[] {"999999999", "abc", "-1"}) {
            ResponseEntity<Map> response = get("/work-orders/" + id, admin());

            assertThat(response.getStatusCode()).as(id).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).containsEntry("code", "NOT_FOUND").containsEntry("path", "/work-orders/" + id)
                    .containsKeys("message", "timestamp");
        }
    }

    @Test
    void everyRoleCanReadAndWithoutATokenIs401() {
        for (String token : List.of(admin(), teamLeader(), production(), technician())) {
            assertThat(get("/work-orders", token).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get("/work-orders/1", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(restTemplate.getForEntity("/work-orders", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity("/work-orders/1", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // =====================================================================================================
    // Alta (REQ-15 a REQ-31)
    // =====================================================================================================

    @Test
    void createReturns201WithAPendingOrderTheServerFieldsAndALocation() {
        Instant before = Instant.now().minusSeconds(2);

        ResponseEntity<Map> response = post(teamLeader(), order("WOCIT-alta", "preventivo", "medium",
                ref("1", "3", "  Hace ruido  ")));

        Instant after = Instant.now().plusSeconds(2);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> created = response.getBody();
        assertThat(created).containsEntry("title", "WOCIT-alta").containsEntry("description", DESCRIPTION)
                .containsEntry("type", "preventivo").containsEntry("priority", "medium")
                .containsEntry("status", "pending").containsEntry("takenBy", null).doesNotContainKey("closingNote");
        String id = (String) created.get("id");
        assertThat(id).matches("[0-9]+");
        assertThat(Long.parseLong(id)).isGreaterThan(32L);
        assertThat(response.getHeaders().getLocation()).hasToString("/work-orders/" + id);
        assertThat((String) created.get("createdAt")).matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d(\\.\\d{1,3})?Z");
        assertThat(Instant.parse((String) created.get("createdAt"))).isBetween(before, after);
        assertThat(get("/work-orders/" + id, admin()).getBody()).isEqualTo(created);
    }

    @Test
    void eachRoleCreatesTheTypesItIsAllowedTo() {
        assertThat(post(teamLeader(), order("WOCIT-tl-prev", "preventivo", "low", ref("1", null, null)))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post(teamLeader(), order("WOCIT-tl-corr", "correctivo", "low", ref("1", null, null)))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post(production(), order("WOCIT-pr-pron", "pronto-intervencion", "high", ref("1", null, null)))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void theRoleTypeMatrixOfForbiddenCombinationsIs403AndCreatesNothing() {
        List<ResponseEntity<Map>> forbidden = new ArrayList<>();
        for (String type : List.of("preventivo", "correctivo", "pronto-intervencion")) {
            forbidden.add(post(admin(), order("WOCIT-admin-" + type, type, "low", ref("1", null, null))));
            forbidden.add(post(technician(), order("WOCIT-tec-" + type, type, "low", ref("1", null, null))));
        }
        forbidden.add(post(teamLeader(), order("WOCIT-tl-x", "pronto-intervencion", "low", ref("1", null, null))));
        forbidden.add(post(production(), order("WOCIT-pr-a", "preventivo", "low", ref("1", null, null))));
        forbidden.add(post(production(), order("WOCIT-pr-b", "correctivo", "low", ref("1", null, null))));

        assertThat(forbidden).hasSize(9).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).containsEntry("code", "FORBIDDEN");
        });
        assertThat(count("select count(*) from work_orders where title like 'WOCIT-%'")).isZero();
    }

    @Test
    void invalidTitleDescriptionTypeAndPriorityAre400OnTheField() {
        assertValidation(post(teamLeader(), order("ab", "correctivo", "low", ref("1", null, null))), "title");
        assertValidation(post(teamLeader(), order("t".repeat(151), "correctivo", "low", ref("1", null, null))), "title");
        assertValidation(post(teamLeader(), order(null, "correctivo", "low", ref("1", null, null))), "title");
        assertValidation(post(teamLeader(), order("   ", "correctivo", "low", ref("1", null, null))), "title");

        Map<String, Object> shortDescription = order("WOCIT-d", "correctivo", "low", ref("1", null, null));
        shortDescription.put("description", "corta");
        assertValidation(post(teamLeader(), shortDescription), "description");
        shortDescription.put("description", "d".repeat(2001));
        assertValidation(post(teamLeader(), shortDescription), "description");
        shortDescription.put("description", null);
        assertValidation(post(teamLeader(), shortDescription), "description");

        assertValidation(post(teamLeader(), order("WOCIT-t", "otro", "low", ref("1", null, null))), "type");
        assertValidation(post(teamLeader(), order("WOCIT-t", null, "low", ref("1", null, null))), "type");
        assertValidation(post(teamLeader(), order("WOCIT-t", "correctivo", "urgent", ref("1", null, null))), "priority");
        assertValidation(post(teamLeader(), order("WOCIT-t", "correctivo", null, ref("1", null, null))), "priority");
        assertThat(count("select count(*) from work_orders where title like 'WOCIT-%'")).isZero();
    }

    @Test
    void theLimitsOfTitleAndDescriptionAreInclusiveAfterTrimming() {
        Map<String, Object> body = order("  abc  ", "correctivo", "low", ref("1", null, null));
        body.put("description", " " + "d".repeat(10) + " ");
        ResponseEntity<Map> minimum = post(teamLeader(), body);

        Map<String, Object> maximum = order(PREFIX + "t".repeat(144), "correctivo", "low", ref("1", null, null));
        maximum.put("description", "d".repeat(2000));

        assertThat(minimum.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(minimum.getBody()).containsEntry("title", "abc").containsEntry("description", "d".repeat(10));
        ResponseEntity<Map> max = post(teamLeader(), maximum);
        assertThat(max.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((String) max.getBody().get("title"))).hasSize(150);
    }

    @Test
    void allFieldErrorsAreReportedTogether() {
        Map<String, Object> body = order("a", "otro", "nope", null);
        body.put("description", "b");

        assertValidation(post(teamLeader(), body), "title", "description", "type", "priority", "machineRef");
    }

    @Test
    void theMachineIsMandatory() {
        Map<String, Object> noRef = order("WOCIT-m", "correctivo", "low", null);
        Map<String, Object> noMachineId = order("WOCIT-m", "correctivo", "low", ref(null, "3", null));

        assertValidation(post(teamLeader(), noRef), "machineRef");
        assertValidation(post(teamLeader(), noMachineId), "machineRef.machineId");
        assertThat(count("select count(*) from work_orders where title like 'WOCIT-%'")).isZero();
    }

    @Test
    void aMachineThatDoesNotExistIsMachineNotFound() {
        for (String machineId : new String[] {"999999999", "", "abc"}) {
            ResponseEntity<Map> response = post(teamLeader(), order("WOCIT-mnf", "correctivo", "low",
                    ref(machineId, null, null)));

            assertThat(response.getStatusCode()).as("'%s'", machineId).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("code", "MACHINE_NOT_FOUND");
        }
        assertThat(count("select count(*) from work_orders where title like 'WOCIT-%'")).isZero();
    }

    @Test
    void aPartThatDoesNotExistOrBelongsToAnotherMachineIsRejected() {
        for (String partId : new String[] {"999999999", "", "abc"}) {
            ResponseEntity<Map> response = post(teamLeader(), order("WOCIT-pnf", "correctivo", "low",
                    ref("1", partId, null)));

            assertThat(response.getStatusCode()).as("'%s'", partId).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("code", "PART_NOT_FOUND");
        }
        // La parte 9 es de la Selladora (máquina 2).
        ResponseEntity<Map> other = post(teamLeader(), order("WOCIT-pom", "correctivo", "low", ref("1", "9", null)));

        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(other.getBody()).containsEntry("code", "PART_OTHER_MACHINE");
        assertThat(count("select count(*) from work_orders where title like 'WOCIT-%'")).isZero();
    }

    @Test
    void withABadMachineAndABadPartTheMachineWins() {
        ResponseEntity<Map> response = post(teamLeader(), order("WOCIT-both", "correctivo", "low",
                ref("999999999", "888888888", null)));

        assertThat(response.getBody()).containsEntry("code", "MACHINE_NOT_FOUND");
    }

    @Test
    void theBreadcrumbIsTheMachineNameAndTheWholeChainOfTheSeededTree() {
        assertThat(breadcrumbOf(create("WOCIT-b0", "1", null))).isEqualTo(ENVASADORA);
        assertThat(breadcrumbOf(create("WOCIT-b1", "1", "1"))).isEqualTo(ENVASADORA + " > Mesa de transporte");
        assertThat(breadcrumbOf(create("WOCIT-b3", "1", "3"))).isEqualTo(MOTOR_CHAIN);
        assertThat(breadcrumbOf(create("WOCIT-b4", "1", "4"))).isEqualTo(MOTOR_CHAIN + " > Rodamiento delantero");
        assertThat(breadcrumbOf(create("WOCIT-b2", "2", "9"))).isEqualTo("Selladora > Cabezal térmico > Resistencia");
    }

    @Test
    void aFifthLevelPartKeepsEveryLevelOfTheBreadcrumb() {
        String machineId = createMachine("WOCIT-M5", "WOCIT Cinco Niveles");
        String parent = null;
        List<String> names = new ArrayList<>(List.of("WOCIT Cinco Niveles"));
        for (int level = 1; level <= 5; level++) {
            parent = createPart(machineId, "Nivel " + level, parent);
            names.add("Nivel " + level);
        }

        Map<String, Object> created = create("WOCIT-b5", machineId, parent);

        assertThat(breadcrumbOf(created)).isEqualTo(String.join(" > ", names));
    }

    @Test
    void theCommentIsSavedTrimmedOnItsOwnAndNotInTheBreadcrumb() {
        Map<String, Object> trimmed = post(teamLeader(), order("WOCIT-c1", "correctivo", "low",
                ref("1", "3", "  Hace ruido  "))).getBody();
        Map<String, Object> absent = post(teamLeader(), order("WOCIT-c2", "correctivo", "low",
                ref("1", "3", null))).getBody();

        assertThat((Map) trimmed.get("machineRef")).containsEntry("comment", "Hace ruido")
                .containsEntry("breadcrumb", MOTOR_CHAIN);
        assertThat((Map) absent.get("machineRef")).containsEntry("comment", "");
    }

    @Test
    void aCommentOf200CharactersIsAcceptedAndOf201Is400() {
        assertThat(post(teamLeader(), order("WOCIT-c200", "correctivo", "low", ref("1", null, "c".repeat(200))))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map> tooLong = post(teamLeader(), order("WOCIT-c201", "correctivo", "low",
                ref("1", null, "c".repeat(201))));

        assertValidation(tooLong, "machineRef.comment");
        assertThat(count("select count(*) from work_orders where title = 'WOCIT-c201'")).isZero();
    }

    @Test
    void whatTheServerDecidesIsIgnoredIfTheClientSendsIt() {
        Map<String, Object> body = order("WOCIT-ignored", "correctivo", "low", ref("1", "3", null));
        body.put("id", "999");
        body.put("status", "completed");
        body.put("createdAt", "2020-01-01T00:00:00Z");
        body.put("takenBy", m("id", "2", "name", "Intruso", "at", "2020-01-01T00:00:00Z"));
        body.put("closingNote", m("comment", "Nota inventada", "authorId", "2", "authorName", "Intruso",
                "at", "2020-01-01T00:00:00Z"));
        ((Map<String, Object>) body.get("machineRef")).put("breadcrumb", "Ruta inventada");

        ResponseEntity<Map> response = post(teamLeader(), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> created = response.getBody();
        assertThat(created.get("id")).isNotEqualTo("999");
        assertThat(created).containsEntry("status", "pending").containsEntry("takenBy", null)
                .doesNotContainKey("closingNote");
        assertThat((String) created.get("createdAt")).doesNotStartWith("2020");
        assertThat(breadcrumbOf(created)).isEqualTo(MOTOR_CHAIN);
        assertThat(count("select count(*) from work_orders where id = 999")).isZero();
    }

    @Test
    void theBreadcrumbIsAPhotoThatSurvivesRenamingTheMachineAndTheParts() {
        String machineId = createMachine("WOCIT-M6", "WOCIT Máquina Original");
        String root = createPart(machineId, "WOCIT Raíz Original", null);
        String leaf = createPart(machineId, "WOCIT Hoja Original", root);
        Map<String, Object> created = create("WOCIT-photo", machineId, leaf);
        String expected = "WOCIT Máquina Original > WOCIT Raíz Original > WOCIT Hoja Original";
        assertThat(breadcrumbOf(created)).isEqualTo(expected);

        assertThat(call(HttpMethod.PUT, "/machines/" + machineId, admin(),
                m("code", "WOCIT-M6", "name", "Renombrada")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.PATCH, "/parts/" + root, admin(), m("name", "Raíz renombrada")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.PATCH, "/parts/" + leaf, admin(), m("name", "Hoja renombrada")).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(breadcrumbOf(get("/work-orders/" + created.get("id"), admin()).getBody())).isEqualTo(expected);
    }

    @Test
    void anOrderKeepsItsMachineRefIfTheMachineAndThePartAreDeleted() {
        String machineId = createMachine("WOCIT-M7", "WOCIT Máquina Efímera");
        String partId = createPart(machineId, "WOCIT Parte Efímera", null);
        Map<String, Object> created = create("WOCIT-history", machineId, partId);

        assertThat(call(HttpMethod.DELETE, "/parts/" + partId, admin(), null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(HttpMethod.DELETE, "/machines/" + machineId, admin(), null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> afterwards = get("/work-orders/" + created.get("id"), admin()).getBody();
        assertThat(afterwards.get("machineRef")).isEqualTo(created.get("machineRef"));
        assertThat((Map) afterwards.get("machineRef")).containsEntry("machineId", machineId)
                .containsEntry("partId", partId)
                .containsEntry("breadcrumb", "WOCIT Máquina Efímera > WOCIT Parte Efímera");
    }

    // =====================================================================================================
    // Edición (REQ-32 a REQ-38)
    // =====================================================================================================

    @Test
    void putChangesTitleDescriptionAndPriorityAndAnswersTheWholeOrder() {
        Map<String, Object> created = create("WOCIT-edit", "1", "3");

        ResponseEntity<Map> response = put((String) created.get("id"), teamLeader(),
                m("title", "  WOCIT-editada  ", "description", "  Descripción nueva y larga  ", "priority", "high"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> updated = response.getBody();
        assertThat(updated).containsEntry("title", "WOCIT-editada").containsEntry("description",
                "Descripción nueva y larga").containsEntry("priority", "high");
        assertThat(updated).containsEntry("id", created.get("id")).containsEntry("type", created.get("type"))
                .containsEntry("status", "pending").containsEntry("createdAt", created.get("createdAt"))
                .containsEntry("machineRef", created.get("machineRef"));
        assertThat(get("/work-orders/" + created.get("id"), admin()).getBody()).isEqualTo(updated);
    }

    @Test
    void putAcceptsTheWholeOrderAsTheFrontendSendsIt() {
        Map<String, Object> created = create("WOCIT-whole", "1", "3");
        Map<String, Object> whole = new LinkedHashMap<>(created);
        whole.put("title", "WOCIT-whole-2");
        whole.put("priority", "low");

        ResponseEntity<Map> response = put((String) created.get("id"), admin(), whole);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("title", "WOCIT-whole-2").containsEntry("priority", "low");
    }

    @Test
    void putWorksInEveryStatusAndKeepsTheStatusTheOwnerAndTheClosingNote() {
        for (String status : List.of("pending", "in-progress", "completed", "cancelled")) {
            Map<String, Object> created = create("WOCIT-st-" + status, "1", null);
            String id = (String) created.get("id");
            force(id, status);
            Map<String, Object> before = get("/work-orders/" + id, admin()).getBody();

            ResponseEntity<Map> response = put(id, teamLeader(), m("title", "WOCIT-st2-" + status,
                    "description", "Descripción editada en " + status, "priority", "high"));

            assertThat(response.getStatusCode()).as(status).isEqualTo(HttpStatus.OK);
            Map<String, Object> after = response.getBody();
            assertThat(after.get("status")).as(status).isEqualTo(status);
            assertThat(after.get("takenBy")).as(status).isEqualTo(before.get("takenBy"));
            assertThat(after.get("closingNote")).as(status).isEqualTo(before.get("closingNote"));
            assertThat(after.get("title")).as(status).isEqualTo("WOCIT-st2-" + status);
        }
    }

    @Test
    void putInvalidFieldsAre400AndTheOrderIsUntouched() {
        Map<String, Object> created = create("WOCIT-inv", "1", null);
        String id = (String) created.get("id");

        assertValidation(put(id, admin(), m("title", "ab", "description", DESCRIPTION, "priority", "low")), "title");
        assertValidation(put(id, admin(), m("title", "t".repeat(151), "description", DESCRIPTION, "priority", "low")),
                "title");
        assertValidation(put(id, admin(), m("title", "WOCIT-x", "description", "corta", "priority", "low")),
                "description");
        assertValidation(put(id, admin(), m("title", "WOCIT-x", "description", "d".repeat(2001), "priority", "low")),
                "description");
        assertValidation(put(id, admin(), m("title", "WOCIT-x", "description", DESCRIPTION, "priority", "urgent")),
                "priority");
        assertValidation(put(id, admin(), m("title", null, "description", null, "priority", null)),
                "title", "description", "priority");
        assertThat(get("/work-orders/" + id, admin()).getBody()).isEqualTo(created);
    }

    @Test
    void putCannotChangeTheTypeTheMachineThePartOrTheComment() {
        Map<String, Object> created = create("WOCIT-imm", "1", "3", "Comentario original");
        String id = (String) created.get("id");

        assertValidation(put(id, admin(), valid(m("type", "preventivo"))), "type");
        assertValidation(put(id, admin(), valid(m("machineRef", m("machineId", "2")))), "machineRef.machineId");
        assertValidation(put(id, admin(), valid(m("machineRef", m("partId", "4")))), "machineRef.partId");
        assertValidation(put(id, admin(), valid(m("machineRef", m("partId", null)))), "machineRef.partId");
        assertValidation(put(id, admin(), valid(m("machineRef", m("comment", "Otro comentario")))),
                "machineRef.comment");
        assertValidation(put(id, admin(), valid(m("type", "preventivo", "machineRef",
                m("machineId", "2", "partId", "9", "comment", "x")))),
                "type", "machineRef.machineId", "machineRef.partId", "machineRef.comment");
        assertThat(get("/work-orders/" + id, admin()).getBody()).isEqualTo(created);
    }

    @Test
    void putAcceptsTheSameOrAbsentTypeAndMachineRef() {
        Map<String, Object> created = create("WOCIT-same", "1", "3", "Comentario original");
        String id = (String) created.get("id");

        assertThat(put(id, admin(), valid(m("type", "correctivo"))).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(put(id, admin(), valid(m("machineRef", m("machineId", "1", "partId", "3",
                "comment", "Comentario original")))).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(put(id, admin(), valid(m("machineRef", m()))).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aNullPartSentToAnOrderOnTheWholeMachineIsNotAChange() {
        Map<String, Object> created = create("WOCIT-whole-machine", "1", null);

        assertThat(put((String) created.get("id"), admin(), valid(m("machineRef", m("partId", null))))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void putIgnoresStatusOwnerClosingNoteCreatedAtIdAndBreadcrumb() {
        Map<String, Object> created = create("WOCIT-ign", "1", "3");
        String id = (String) created.get("id");
        Map<String, Object> body = valid(m("id", "999", "status", "completed", "createdAt", "2020-01-01T00:00:00Z",
                "takenBy", m("id", "2", "name", "Intruso", "at", "2020-01-01T00:00:00Z"),
                "closingNote", m("comment", "Nota inventada", "authorId", "2", "authorName", "Intruso",
                        "at", "2020-01-01T00:00:00Z"),
                "machineRef", m("breadcrumb", "Ruta inventada")));

        ResponseEntity<Map> response = put(id, admin(), body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("id", id).containsEntry("status", "pending")
                .containsEntry("takenBy", null).doesNotContainKey("closingNote")
                .containsEntry("createdAt", created.get("createdAt"));
        assertThat(breadcrumbOf(response.getBody())).isEqualTo(MOTOR_CHAIN);
    }

    @Test
    void putOfAnUnknownOrNonNumericOrderIs404AndCreatesNothing() {
        for (String id : new String[] {"999999999", "abc"}) {
            ResponseEntity<Map> response = put(id, admin(), valid(m()));

            assertThat(response.getStatusCode()).as(id).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).containsEntry("code", "NOT_FOUND");
        }
        assertThat(count("select count(*) from work_orders where title = 'WOCIT-valid'")).isZero();
    }

    @Test
    void productionAndTechnicianGet403OnPutAndTheOrderIsUntouched() {
        Map<String, Object> created = create("WOCIT-put403", "1", null);
        String id = (String) created.get("id");

        for (String token : List.of(production(), technician())) {
            assertForbidden(put(id, token, valid(m())));
        }

        assertThat(get("/work-orders/" + id, admin()).getBody()).isEqualTo(created);
    }

    // =====================================================================================================
    // Baja (REQ-39, REQ-40, REQ-41)
    // =====================================================================================================

    @Test
    void administratorDeletesAnOrderInEveryStatus() {
        for (String status : List.of("pending", "in-progress", "completed", "cancelled")) {
            String id = (String) create("WOCIT-del-" + status, "1", null).get("id");
            force(id, status);

            assertThat(call(HttpMethod.DELETE, "/work-orders/" + id, admin(), null).getStatusCode())
                    .as(status).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(get("/work-orders/" + id, admin()).getStatusCode()).as(status).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void deleteOfAnUnknownOrNonNumericOrderIs404() {
        for (String id : new String[] {"999999999", "abc"}) {
            assertThat(call(HttpMethod.DELETE, "/work-orders/" + id, admin(), null).getStatusCode()).as(id)
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void everyRoleButTheAdministratorGets403OnDeleteAndTheOrderStays() {
        Map<String, Object> created = create("WOCIT-del403", "1", null);
        String id = (String) created.get("id");

        for (String token : List.of(teamLeader(), production(), technician())) {
            assertForbidden(call(HttpMethod.DELETE, "/work-orders/" + id, token, null));
        }

        assertThat(get("/work-orders/" + id, admin()).getBody()).isEqualTo(created);
    }

    // =====================================================================================================
    // Orden de los errores (REQ-46)
    // =====================================================================================================

    @Test
    void theForbiddenWinsOverAnInvalidBodyAndOverAMalformedId() {
        assertForbidden(post(admin(), new LinkedHashMap<>()));
        assertForbidden(post(technician(), order("a", "otro", "nope", null)));
        // Tipo válido que el rol no crea, con otros campos inválidos: 403 y no 400.
        assertForbidden(post(teamLeader(), order("a", "pronto-intervencion", "nope", null)));
        assertForbidden(post(production(), order("a", "preventivo", "nope", ref("999999999", null, null))));
        assertForbidden(put("abc", production(), m("title", "a")));
        assertForbidden(call(HttpMethod.DELETE, "/work-orders/abc", teamLeader(), null));
    }

    @Test
    void anInvalidTypeIs400AndNotForbiddenForTheRolesThatCreate() {
        assertValidation(post(teamLeader(), order("WOCIT-t", "otro", "low", ref("1", null, null))), "type");
        assertValidation(post(production(), order("WOCIT-t", "otro", "low", ref("1", null, null))), "type");
    }

    @Test
    void aFormatErrorWinsOverAnUnresolvedReference() {
        ResponseEntity<Map> response = post(teamLeader(), order("a", "correctivo", "low",
                ref("999999999", "888888888", null)));

        assertValidation(response, "title");
    }

    @Test
    void aFormatErrorOnPutWinsOverANotFoundAndANotFoundWinsOverAnAttemptToChange() {
        assertValidation(put("999999999", admin(), m("title", "a", "description", "b", "priority", "x")),
                "title", "description", "priority");
        assertThat(put("999999999", admin(), valid(m("type", "preventivo", "machineRef", m("machineId", "2"))))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // =====================================================================================================
    // Ayudas
    // =====================================================================================================

    /** Crea una orden válida como team leader (tipo correctivo). */
    private Map<String, Object> create(String title, String machineId, String partId) {
        return create(title, machineId, partId, null);
    }

    private Map<String, Object> create(String title, String machineId, String partId, String comment) {
        ResponseEntity<Map> response = post(teamLeader(), order(title, "correctivo", "medium",
                ref(machineId, partId, comment)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private static Map<String, Object> order(String title, String type, String priority, Map<String, Object> ref) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("description", DESCRIPTION);
        body.put("type", type);
        body.put("priority", priority);
        if (ref != null) {
            body.put("machineRef", ref);
        }
        return body;
    }

    private static Map<String, Object> ref(String machineId, String partId, String comment) {
        Map<String, Object> ref = new LinkedHashMap<>();
        if (machineId != null) {
            ref.put("machineId", machineId);
        }
        if (partId != null) {
            ref.put("partId", partId);
        }
        if (comment != null) {
            ref.put("comment", comment);
        }
        return ref;
    }

    /** Mapa JSON que admite valores nulos, para distinguir "ausente" de "enviado como null". */
    private static Map<String, Object> m(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    /** Un cuerpo de {@code PUT} válido al que se le suman los campos dados. */
    private static Map<String, Object> valid(Map<String, Object> extra) {
        Map<String, Object> body = m("title", "WOCIT-valid", "description", DESCRIPTION, "priority", "low");
        body.putAll(extra);
        return body;
    }

    private String createMachine(String code, String name) {
        ResponseEntity<Map> response = call(HttpMethod.POST, "/machines", admin(), m("code", code, "name", name));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    private String createPart(String machineId, String name, String parentId) {
        ResponseEntity<Map> response = call(HttpMethod.POST, "/machines/" + machineId + "/parts", admin(),
                m("name", name, "parentId", parentId));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    /**
     * Fuerza por SQL el estado, el dueño y la nota de cierre: cambiarlos por la API es de la spec 04.
     * Un solo {@code UPDATE}, porque los {@code CHECK} de {@code V8} rechazan el paso intermedio de una
     * orden cerrada sin nota.
     */
    private void force(String id, String status) {
        if (status.equals("pending")) {
            return;
        }
        boolean closed = status.equals("completed") || status.equals("cancelled");
        jdbcTemplate.update("update work_orders set status = ?, taken_by_id = u.id, "
                + "taken_by_name = 'Técnico Mecánico de Guardia', taken_at = now()"
                + (closed ? ", closing_comment = 'Se resolvió y se verificó el funcionamiento.', "
                        + "closing_author_id = u.id, closing_author_name = 'Técnico Mecánico de Guardia', "
                        + "closed_at = now()" : "")
                + " from users u where u.username = 'tecnico' and work_orders.id = ?", status, Long.valueOf(id));
    }

    private static String breadcrumbOf(Map<String, Object> order) {
        return (String) ((Map) order.get("machineRef")).get("breadcrumb");
    }

    private static List<String> titles(ResponseEntity<Map> page) {
        return ((List<Map<String, Object>>) page.getBody().get("data")).stream()
                .map(o -> (String) o.get("title")).toList();
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

    private ResponseEntity<Map> get(String path, String token, Object... uriVariables) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), Map.class, uriVariables);
    }

    private ResponseEntity<Map> post(String token, Object body) {
        return call(HttpMethod.POST, "/work-orders", token, body);
    }

    private ResponseEntity<Map> put(String id, String token, Object body) {
        return call(HttpMethod.PUT, "/work-orders/" + id, token, body);
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

    private int count(String sql) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class);
        return value.intValue();
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
