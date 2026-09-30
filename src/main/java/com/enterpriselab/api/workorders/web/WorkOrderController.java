package com.enterpriselab.api.workorders.web;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.enterpriselab.api.shared.web.ApiError;
import com.enterpriselab.api.shared.web.AuthenticatedRole;
import com.enterpriselab.api.shared.web.OpenApiConfig;
import com.enterpriselab.api.shared.web.PageResponse;
import com.enterpriselab.api.workorders.domain.WorkOrder;
import com.enterpriselab.api.workorders.domain.WorkOrderQuery;
import com.enterpriselab.api.workorders.domain.WorkOrderService;

/**
 * Órdenes de trabajo (REQ-1 a REQ-46). Solo traduce HTTP a {@link WorkOrderService}:
 * extrae el rol del token y delega. Los parámetros de consulta llegan como
 * {@code String} y los valida el dominio, para que uno inválido sea {@code 400
 * VALIDATION_ERROR}; el id de la URL también es un {@code String}: uno que no es un
 * número lo trata el dominio como una orden que no existe ({@code 404}).
 */
@RestController
@RequestMapping("/work-orders")
@Tag(name = "Work orders", description = "Órdenes de trabajo: listado paginado, alta, edición y baja")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "El rol no puede hacer esta operación",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class WorkOrderController {

    private static final String ERROR_404 = "No existe una orden con ese id (NOT_FOUND)";

    private final WorkOrderService service;

    public WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista las órdenes, paginadas",
            description = "Por id ascendente. Filtros opcionales y combinables por título, estado y prioridad. "
                    + "Una página fuera de rango devuelve data vacío con los totales reales. "
                    + "Disponible para cualquier usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de órdenes"),
            @ApiResponse(responseCode = "400",
                    description = "Parámetro inválido (VALIDATION_ERROR), con el detalle por parámetro",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PageResponse<WorkOrderResponse> list(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Página, desde 1 (por defecto 1)") @RequestParam(required = false) String page,
            @Parameter(description = "Tamaño de página, de 1 a 100 (por defecto 10)")
            @RequestParam(required = false) String size,
            @Parameter(description = "Texto contenido en el título, sin distinguir mayúsculas; vacío no filtra")
            @RequestParam(required = false) String title,
            @Parameter(description = "pending, in-progress, completed o cancelled")
            @RequestParam(required = false) String status,
            @Parameter(description = "low, medium o high") @RequestParam(required = false) String priority) {
        return PageResponse.from(
                service.list(AuthenticatedRole.from(jwt), new WorkOrderQuery(page, size, title, status, priority)),
                WorkOrderResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta una orden por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La orden completa, con machineRef, takenBy y closingNote "
                    + "cuando existan"),
            @ApiResponse(responseCode = "404", description = ERROR_404,
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public WorkOrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return WorkOrderResponse.from(service.get(AuthenticatedRole.from(jwt), id));
    }

    @PostMapping
    @Operation(summary = "Da de alta una orden",
            description = "Team leader crea preventivo y correctivo; producción crea pronto-intervencion. "
                    + "El servidor fija el estado inicial (pending), createdAt y el breadcrumb; "
                    + "esos campos se ignoran si el cliente los envía.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Orden creada"),
            @ApiResponse(responseCode = "400",
                    description = "Datos inválidos (VALIDATION_ERROR), máquina inexistente (MACHINE_NOT_FOUND), "
                            + "parte inexistente (PART_NOT_FOUND) o de otra máquina (PART_OTHER_MACHINE)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<WorkOrderResponse> create(@AuthenticationPrincipal Jwt jwt,
            @RequestBody WorkOrderCreateRequest request) {
        WorkOrder created = service.create(AuthenticatedRole.from(jwt), request.toCommand());
        return ResponseEntity.created(URI.create("/work-orders/" + created.id()))
                .body(WorkOrderResponse.from(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita título, descripción y prioridad de una orden",
            description = "Solo esos tres campos cambian, en cualquier estado. El tipo y la referencia a máquina "
                    + "no se editan: un valor distinto se rechaza. El estado, el dueño y la nota de cierre no se "
                    + "tocan por esta vía: si el cliente los envía se ignoran.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La orden completa actualizada"),
            @ApiResponse(responseCode = "400",
                    description = "Datos inválidos o intento de cambiar el tipo o la máquina (VALIDATION_ERROR)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = ERROR_404,
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public WorkOrderResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestBody WorkOrderUpdateRequest request) {
        return WorkOrderResponse.from(service.update(AuthenticatedRole.from(jwt), id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Da de baja una orden",
            description = "Solo el administrador, en cualquier estado.")
    @ApiResponse(responseCode = "404", description = ERROR_404,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        service.delete(AuthenticatedRole.from(jwt), id);
    }
}
