package com.enterpriselab.api.machines.web;

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

import com.enterpriselab.api.machines.domain.Machine;
import com.enterpriselab.api.machines.domain.MachineService;
import com.enterpriselab.api.shared.web.ApiError;
import com.enterpriselab.api.shared.web.AuthenticatedRole;
import com.enterpriselab.api.shared.web.OpenApiConfig;

/**
 * Máquinas (REQ-1 a REQ-15, REQ-31, REQ-32, REQ-36, REQ-37). Solo traduce HTTP a
 * {@link MachineService}: extrae el rol del token y delega. El id de la URL es
 * un {@code String}: uno que no es un número lo trata el dominio como una
 * máquina que no existe ({@code 404}).
 */
@RestController
@RequestMapping("/machines")
@Tag(name = "Machines", description = "Maestro de máquinas")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403",
                description = "Escribir requiere administrador o team leader de mantenimiento",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class MachineController {

    private static final String ERROR_400 = "Datos inválidos (VALIDATION_ERROR), con el detalle por campo";
    private static final String ERROR_404 = "No existe una máquina con ese id (NOT_FOUND)";
    private static final String ERROR_409_CODE = "El código ya pertenece a otra máquina (DUPLICATE_MACHINE_CODE)";

    private final MachineService service;

    public MachineController(MachineService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista todas las máquinas",
            description = "Colección completa, sin paginar, por orden de alta, con la cantidad de partes de cada una. "
                    + "Disponible para cualquier usuario autenticado.")
    public List<MachineResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(AuthenticatedRole.from(jwt)).stream().map(MachineResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta una máquina por id")
    @ApiResponse(responseCode = "404", description = ERROR_404,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public MachineResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return MachineResponse.from(service.get(AuthenticatedRole.from(jwt), id));
    }

    @PostMapping
    @Operation(summary = "Da de alta una máquina",
            description = "El código se guarda sin espacios en los bordes y en mayúsculas "
                    + "(de 1 a 20 letras, dígitos o guiones, sin empezar con guion).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Máquina creada"),
            @ApiResponse(responseCode = "400", description = ERROR_400,
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = ERROR_409_CODE,
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<MachineResponse> create(@AuthenticationPrincipal Jwt jwt,
            @RequestBody MachineRequest request) {
        Machine created = service.create(AuthenticatedRole.from(jwt), request.toCommand());
        return ResponseEntity.created(URI.create("/machines/" + created.id())).body(MachineResponse.from(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita el código y el nombre de una máquina",
            description = "No cambia el id ni las partes. La máquina no es duplicada de sí misma.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = ERROR_400,
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = ERROR_404,
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = ERROR_409_CODE,
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MachineResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestBody MachineRequest request) {
        return MachineResponse.from(service.update(AuthenticatedRole.from(jwt), id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Da de baja una máquina", description = "Sin cascada: una máquina con partes no se elimina.")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = ERROR_404,
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "La máquina tiene partes (MACHINE_HAS_PARTS)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        service.delete(AuthenticatedRole.from(jwt), id);
    }
}
