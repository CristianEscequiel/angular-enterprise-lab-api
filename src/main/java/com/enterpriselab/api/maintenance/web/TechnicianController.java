package com.enterpriselab.api.maintenance.web;

import java.net.URI;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.enterpriselab.api.maintenance.domain.Technician;
import com.enterpriselab.api.maintenance.domain.TechnicianService;
import com.enterpriselab.api.shared.web.ApiError;
import com.enterpriselab.api.shared.web.AuthenticatedRole;
import com.enterpriselab.api.shared.web.OpenApiConfig;

/**
 * Técnicos del maestro de mantenimiento (REQ-1 a REQ-19). Solo traduce HTTP a
 * {@link TechnicianService}: extrae el rol del token y delega. Quién puede qué y
 * qué datos son válidos lo decide el dominio; el legajo de la URL es un
 * {@code String} sin validar, para que el {@code 403} le gane al {@code 400}.
 */
@RestController
@RequestMapping("/technicians")
@Tag(name = "Technicians", description = "Maestro de técnicos de mantenimiento")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "El rol no tiene permiso para la operación",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class TechnicianController {

    private static final String ERROR_400 = "Datos inválidos (VALIDATION_ERROR), con el detalle por campo";
    private static final String ERROR_404 = "No existe un técnico con ese legajo (NOT_FOUND)";

    private final TechnicianService service;

    public TechnicianController(TechnicianService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista todos los técnicos",
            description = "Colección completa, sin paginar, por orden de alta. Administrador y team leader.")
    public List<TechnicianResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(AuthenticatedRole.from(jwt)).stream().map(TechnicianResponse::from).toList();
    }

    @GetMapping("/{legajo}")
    @Operation(summary = "Consulta un técnico por legajo", description = "Administrador y team leader.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = ERROR_400,
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = ERROR_404,
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public TechnicianResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String legajo) {
        return TechnicianResponse.from(service.get(AuthenticatedRole.from(jwt), legajo));
    }

    @PostMapping
    @Operation(summary = "Da de alta un técnico",
            description = "Sin usuario de login. Administrador y team leader.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = ERROR_400,
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe un técnico con ese legajo (DUPLICATE_LEGAJO)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<TechnicianResponse> create(@AuthenticationPrincipal Jwt jwt,
            @RequestBody TechnicianRequest request) {
        Technician created = service.create(AuthenticatedRole.from(jwt), request.toCommand());
        return ResponseEntity.created(URI.create("/technicians/" + created.legajo()))
                .body(TechnicianResponse.from(created));
    }

    @PutMapping("/{legajo}")
    @Operation(summary = "Edita nombre, apellido, especialidad y tipo de equipo",
            description = "El legajo no se edita: si el cuerpo trae otro, responde 400. Administrador y team leader.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = ERROR_400,
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = ERROR_404,
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public TechnicianResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String legajo,
            @RequestBody TechnicianRequest request) {
        return TechnicianResponse.from(service.update(AuthenticatedRole.from(jwt), legajo, request.toCommand()));
    }

    @DeleteMapping("/{legajo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Da de baja un técnico",
            description = "Solo administrador. Bloqueada si el técnico tiene usuario de login o es miembro de un equipo.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = ERROR_400,
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = ERROR_404,
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Tiene usuario de acceso o es miembro de un equipo (TECHNICIAN_IN_USE)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String legajo) {
        service.delete(AuthenticatedRole.from(jwt), legajo);
    }
}
