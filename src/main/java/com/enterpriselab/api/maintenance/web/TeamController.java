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

import com.enterpriselab.api.maintenance.domain.Team;
import com.enterpriselab.api.maintenance.domain.TeamService;
import com.enterpriselab.api.shared.web.ApiError;
import com.enterpriselab.api.shared.web.AuthenticatedRole;
import com.enterpriselab.api.shared.web.OpenApiConfig;

/**
 * Equipos de mantenimiento (REQ-20 a REQ-35, REQ-40, REQ-41). Solo traduce HTTP a
 * {@link TeamService}: extrae el rol del token y delega. El id de la URL es un
 * {@code String}: uno que no es un número lo trata el dominio como un equipo que
 * no existe ({@code 404}).
 */
@RestController
@RequestMapping("/teams")
@Tag(name = "Teams", description = "Equipos de mantenimiento y sus miembros")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403",
                description = "Solo el team leader de mantenimiento puede operar equipos (el administrador tampoco)",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class TeamController {

    private static final String ERROR_400 = "Datos inválidos (VALIDATION_ERROR), con el detalle por campo, "
            + "o un legajo de miembro que no existe (UNKNOWN_TECHNICIAN)";
    private static final String ERROR_404 = "No existe un equipo con ese id (NOT_FOUND)";

    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista todos los equipos",
            description = "Colección completa, sin paginar, por orden de alta, con los miembros en el orden guardado.")
    public List<TeamResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(AuthenticatedRole.from(jwt)).stream().map(TeamResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta un equipo por id")
    @ApiResponse(responseCode = "404", description = ERROR_404,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public TeamResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return TeamResponse.from(service.get(AuthenticatedRole.from(jwt), id));
    }

    @PostMapping
    @Operation(summary = "Da de alta un equipo",
            description = "Los miembros se indican por legajo: deben existir, no repetirse y tener 1 a 8 dígitos.")
    @ApiResponse(responseCode = "400", description = ERROR_400,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<TeamResponse> create(@AuthenticationPrincipal Jwt jwt, @RequestBody TeamRequest request) {
        Team created = service.create(AuthenticatedRole.from(jwt), request.toCommand());
        return ResponseEntity.created(URI.create("/teams/" + created.id())).body(TeamResponse.from(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reemplaza nombre, tipo y la lista completa de miembros",
            description = "Los miembros se validan igual que en el alta. Es todo o nada: si algo falla, el equipo no cambia.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = ERROR_400,
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = ERROR_404,
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public TeamResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestBody TeamRequest request) {
        return TeamResponse.from(service.update(AuthenticatedRole.from(jwt), id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Da de baja un equipo", description = "Los técnicos que eran miembros quedan intactos.")
    @ApiResponse(responseCode = "404", description = ERROR_404,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        service.delete(AuthenticatedRole.from(jwt), id);
    }
}
