package com.enterpriselab.api.dashboard.web;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.enterpriselab.api.dashboard.domain.DashboardQuery;
import com.enterpriselab.api.dashboard.domain.DashboardService;
import com.enterpriselab.api.shared.web.ApiError;
import com.enterpriselab.api.shared.web.AuthenticatedRole;
import com.enterpriselab.api.shared.web.OpenApiConfig;

/**
 * Indicadores del tablero (REQ-1 a REQ-20). Solo traduce HTTP a {@link DashboardService}:
 * extrae el rol del token y pasa los parámetros crudos (como {@code String}, para que una
 * fecha inválida sea {@code 400 VALIDATION_ERROR}).
 */
@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "Indicadores de las órdenes de trabajo (solo lectura)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @Operation(summary = "Resumen del tablero",
            description = "Conteos de todas las órdenes por estado, prioridad y tipo, más total y abiertas. "
                    + "El período (from y to, YYYY-MM-DD en UTC, ambos incluidos; por defecto los últimos 30 días) "
                    + "solo afecta a las órdenes cerradas en el período y al tiempo promedio de resolución en "
                    + "minutos (solo completed). Disponible para cualquier usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El resumen"),
            @ApiResponse(responseCode = "400",
                    description = "from o to no son una fecha YYYY-MM-DD válida, o from es posterior a to "
                            + "(VALIDATION_ERROR)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public DashboardSummaryResponse summary(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Inicio del período, YYYY-MM-DD (por defecto, 29 días antes de to)")
            @RequestParam(required = false) String from,
            @Parameter(description = "Fin del período, YYYY-MM-DD (por defecto, hoy en UTC)")
            @RequestParam(required = false) String to) {
        return DashboardSummaryResponse.from(service.summary(AuthenticatedRole.from(jwt),
                new DashboardQuery(from, to)));
    }

    @GetMapping("/workload")
    @Operation(summary = "Órdenes en progreso por técnico",
            description = "Un elemento por técnico con órdenes in-progress, por cantidad descendente y nombre "
                    + "ascendente. Solo administrador y team leader.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La lista, vacía si no hay órdenes en progreso"),
            @ApiResponse(responseCode = "403", description = "El rol no puede ver la carga de trabajo",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<WorkloadItemResponse> workload(@AuthenticationPrincipal Jwt jwt) {
        return service.workload(AuthenticatedRole.from(jwt)).stream().map(WorkloadItemResponse::from).toList();
    }
}
