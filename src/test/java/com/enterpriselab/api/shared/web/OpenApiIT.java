package com.enterpriselab.api.shared.web;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-14 (spec 00) y REQ-38 (spec 01): la documentación es pública y describe
 * los endpoints de auth, técnicos y equipos.
 */
@ActiveProfiles("dev")
class OpenApiIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void apiDocsListLoginAndMeWithBearerScheme() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/v3/api-docs", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> paths = (Map<String, Object>) response.getBody().get("paths");
        assertThat(paths).containsKeys("/auth/login", "/auth/me");
        Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
        assertThat((Map<String, Object>) components.get("securitySchemes")).containsKey("bearerAuth");
    }

    /** REQ-38: todos los endpoints de técnicos y equipos, generados desde las anotaciones, con {@code bearerAuth}. */
    @Test
    @SuppressWarnings("unchecked")
    void apiDocsListEveryTechnicianAndTeamEndpointWithTheBearerScheme() {
        Map<String, Object> body = restTemplate.getForEntity("/v3/api-docs", Map.class).getBody();
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) body.get("paths");

        assertThat(paths).containsKeys("/technicians", "/technicians/{legajo}", "/teams", "/teams/{id}");
        assertThat(paths.get("/technicians")).containsOnlyKeys("get", "post");
        assertThat(paths.get("/technicians/{legajo}")).containsOnlyKeys("get", "put", "delete");
        assertThat(paths.get("/teams")).containsOnlyKeys("get", "post");
        assertThat(paths.get("/teams/{id}")).containsOnlyKeys("get", "put", "delete");

        for (String path : List.of("/technicians", "/technicians/{legajo}", "/teams", "/teams/{id}")) {
            paths.get(path).forEach((method, operation) -> {
                Map<String, Object> details = (Map<String, Object>) operation;
                assertThat(details.get("security")).as(method + " " + path).isEqualTo(
                        List.of(Map.of(OpenApiConfig.BEARER_AUTH, List.of())));
                assertThat(details).as(method + " " + path).containsKey("summary");
                assertThat((Map<String, Object>) details.get("responses")).as(method + " " + path)
                        .containsKeys("401", "403");
            });
        }

        Map<String, Object> deleteTechnician = (Map<String, Object>) paths.get("/technicians/{legajo}").get("delete");
        assertThat((Map<String, Object>) deleteTechnician.get("responses")).containsKeys("204", "400", "404", "409");
        Map<String, Object> components = (Map<String, Object>) body.get("components");
        assertThat((Map<String, Object>) components.get("securitySchemes")).containsKey("bearerAuth");
    }

    /** REQ-35 (spec 02): todos los endpoints de máquinas y partes, generados desde las anotaciones, con {@code bearerAuth}. */
    @Test
    @SuppressWarnings("unchecked")
    void apiDocsListEveryMachineAndPartEndpointWithTheBearerScheme() {
        Map<String, Object> body = restTemplate.getForEntity("/v3/api-docs", Map.class).getBody();
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) body.get("paths");
        List<String> machinePaths = List.of("/machines", "/machines/{id}", "/machines/{machineId}/parts",
                "/parts/{id}");

        assertThat(paths).containsKeys(machinePaths.toArray(String[]::new));
        assertThat(paths.get("/machines")).containsOnlyKeys("get", "post");
        assertThat(paths.get("/machines/{id}")).containsOnlyKeys("get", "put", "delete");
        assertThat(paths.get("/machines/{machineId}/parts")).containsOnlyKeys("get", "post");
        assertThat(paths.get("/parts/{id}")).containsOnlyKeys("patch", "delete");

        for (String path : machinePaths) {
            paths.get(path).forEach((method, operation) -> {
                Map<String, Object> details = (Map<String, Object>) operation;
                assertThat(details.get("security")).as(method + " " + path).isEqualTo(
                        List.of(Map.of(OpenApiConfig.BEARER_AUTH, List.of())));
                assertThat(details).as(method + " " + path).containsKey("summary");
                assertThat((Map<String, Object>) details.get("responses")).as(method + " " + path)
                        .containsKeys("401", "403");
            });
        }

        assertThat((Map<String, Object>) ((Map<String, Object>) paths.get("/machines/{id}").get("delete"))
                .get("responses")).containsKeys("204", "404", "409");
        assertThat((Map<String, Object>) ((Map<String, Object>) paths.get("/parts/{id}").get("delete"))
                .get("responses")).containsKeys("204", "404", "409");
        assertThat((Map<String, Object>) ((Map<String, Object>) paths.get("/machines/{machineId}/parts").get("post"))
                .get("responses")).containsKeys("201", "400", "404");
        assertThat((Map<String, Object>) ((Map<String, Object>) paths.get("/machines").get("post"))
                .get("responses")).containsKeys("201", "400", "409");
        Map<String, Map<String, Object>> schemas = (Map<String, Map<String, Object>>) ((Map<String, Object>) body
                .get("components")).get("schemas");
        assertThat((Map<String, Object>) schemas.get("MachineResponse").get("properties"))
                .containsOnlyKeys("id", "code", "name", "partCount");
        assertThat((Map<String, Object>) schemas.get("PartResponse").get("properties"))
                .containsOnlyKeys("id", "machineId", "parentId", "name");
    }

    /** REQ-45 (spec 03): todos los endpoints de órdenes, generados desde las anotaciones, con {@code bearerAuth}. */
    @Test
    @SuppressWarnings("unchecked")
    void apiDocsListEveryWorkOrderEndpointWithTheBearerSchemeAndTheListParameters() {
        Map<String, Object> body = restTemplate.getForEntity("/v3/api-docs", Map.class).getBody();
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) body.get("paths");

        assertThat(paths).containsKeys("/work-orders", "/work-orders/{id}");
        assertThat(paths.get("/work-orders")).containsOnlyKeys("get", "post");
        assertThat(paths.get("/work-orders/{id}")).containsOnlyKeys("get", "put", "delete");

        for (String path : List.of("/work-orders", "/work-orders/{id}")) {
            paths.get(path).forEach((method, operation) -> {
                Map<String, Object> details = (Map<String, Object>) operation;
                assertThat(details.get("security")).as(method + " " + path).isEqualTo(
                        List.of(Map.of(OpenApiConfig.BEARER_AUTH, List.of())));
                assertThat(details).as(method + " " + path).containsKey("summary");
                assertThat((Map<String, Object>) details.get("responses")).as(method + " " + path)
                        .containsKeys("401", "403");
            });
        }

        Map<String, Object> list = (Map<String, Object>) paths.get("/work-orders").get("get");
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) list.get("parameters");
        assertThat(parameters).extracting(p -> p.get("name"))
                .containsExactlyInAnyOrder("page", "size", "title", "status", "priority");
        assertThat(parameters).allSatisfy(p -> assertThat(p.get("in")).isEqualTo("query"));
        assertThat((Map<String, Object>) list.get("responses")).containsKeys("200", "400");

        assertThat((Map<String, Object>) ((Map<String, Object>) paths.get("/work-orders").get("post"))
                .get("responses")).containsKeys("201", "400");
        assertThat((Map<String, Object>) ((Map<String, Object>) paths.get("/work-orders/{id}").get("get"))
                .get("responses")).containsKeys("200", "404");
        assertThat((Map<String, Object>) ((Map<String, Object>) paths.get("/work-orders/{id}").get("put"))
                .get("responses")).containsKeys("200", "400", "404");
        assertThat((Map<String, Object>) ((Map<String, Object>) paths.get("/work-orders/{id}").get("delete"))
                .get("responses")).containsKeys("204", "404");

        for (String action : List.of("take", "close", "release")) {
            Map<String, Object> flow = (Map<String, Object>) paths.get("/work-orders/{id}/" + action);
            assertThat(flow).as(action).containsOnlyKeys("post");
            Map<String, Object> post = (Map<String, Object>) flow.get("post");
            assertThat(post.get("security")).as(action)
                    .isEqualTo(List.of(Map.of(OpenApiConfig.BEARER_AUTH, List.of())));
            assertThat((Map<String, Object>) post.get("responses")).as(action)
                    .containsKeys("200", "401", "403", "404", "409");
        }
        Map<String, Object> closePost = (Map<String, Object>) paths.get("/work-orders/{id}/close").get("post");
        assertThat((Map<String, Object>) closePost.get("responses")).containsKey("400");

        Map<String, Map<String, Object>> schemas = (Map<String, Map<String, Object>>) ((Map<String, Object>) body
                .get("components")).get("schemas");
        assertThat((Map<String, Object>) schemas.get("WorkOrderResponse").get("properties")).containsOnlyKeys("id",
                "title", "description", "machineRef", "type", "priority", "status", "createdAt", "takenBy",
                "closingNote");
        assertThat((Map<String, Object>) schemas.get("WorkOrderCloseRequest").get("properties"))
                .containsOnlyKeys("outcome", "comment");
        assertThat((Map<String, Object>) schemas.get("MachineRefResponse").get("properties"))
                .containsOnlyKeys("machineId", "partId", "breadcrumb", "comment");
        assertThat((Map<String, Object>) schemas.get("PageResponseWorkOrderResponse").get("properties"))
                .containsOnlyKeys("data", "page", "size", "totalItems", "totalPages");
    }

    /**
     * Enmienda 00-A: el contrato de la sesión (REQ-17, REQ-20) está en la documentación generada
     * desde el código: el login devuelve {@code {token, user}} y {@code /auth/me} el mismo
     * {@code UserResponse}, sin ningún campo de contraseña (REQ-22).
     */
    @Test
    @SuppressWarnings("unchecked")
    void apiDocsDescribeTheSessionContractWithTheUserAndNoPassword() {
        Map<String, Object> body = restTemplate.getForEntity("/v3/api-docs", Map.class).getBody();
        Map<String, Map<String, Object>> schemas =
                (Map<String, Map<String, Object>>) ((Map<String, Object>) body.get("components")).get("schemas");

        Map<String, Object> loginProperties = (Map<String, Object>) schemas.get("LoginResponse").get("properties");
        Map<String, Object> userProperties = (Map<String, Object>) schemas.get("UserResponse").get("properties");

        assertThat(loginProperties).containsOnlyKeys("token", "user");
        assertThat(userProperties).containsOnlyKeys("id", "username", "displayName", "email", "role", "legajo",
                "specialty", "teamType");
        assertThat(schemas).doesNotContainKey("MeResponse");
        // /auth/me responde con el mismo UserResponse que el login.
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) body.get("paths");
        assertThat(paths.get("/auth/me").toString()).contains("UserResponse");
    }

    @Test
    void swaggerUiIsReachableWithoutToken() {
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
