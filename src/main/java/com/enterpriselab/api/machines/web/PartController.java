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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.enterpriselab.api.machines.domain.Part;
import com.enterpriselab.api.machines.domain.PartService;
import com.enterpriselab.api.shared.web.ApiError;
import com.enterpriselab.api.shared.web.AuthenticatedRole;
import com.enterpriselab.api.shared.web.OpenApiConfig;

/**
 * Árbol de partes (REQ-16 a REQ-32, REQ-36, REQ-37). Las partes de una máquina
 * cuelgan de ella para listarlas y crearlas ({@code /machines/{machineId}/parts})
 * y se editan y eliminan por su id ({@code /parts/{id}}); viven en una sola clase
 * para no partir el recurso. Los ids de la URL son {@code String}: uno que no
 * es un número lo trata el dominio como un recurso que no existe ({@code 404}).
 */
@RestController
@Tag(name = "Parts", description = "Árbol de partes de una máquina, como lista plana con parentId")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403",
                description = "Escribir requiere administrador o team leader de mantenimiento",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class PartController {

    private static final String MACHINE_404 = "No existe una máquina con ese id (NOT_FOUND)";
    private static final String PART_404 = "No existe una parte con ese id (NOT_FOUND)";

    private final PartService service;

    public PartController(PartService service) {
        this.service = service;
    }

    @GetMapping("/machines/{machineId}/parts")
    @Operation(summary = "Lista las partes de una máquina",
            description = "Lista plana, en orden de creación, con el parentId de cada parte (null en las de primer "
                    + "nivel): armar el árbol es cosa del cliente. Disponible para cualquier usuario autenticado.")
    @ApiResponse(responseCode = "404", description = MACHINE_404,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public List<PartResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String machineId) {
        return service.listByMachine(AuthenticatedRole.from(jwt), machineId).stream()
                .map(PartResponse::from).toList();
    }

    @PostMapping("/machines/{machineId}/parts")
    @Operation(summary = "Da de alta una parte",
            description = "Con parentId nulo o ausente es de primer nivel; si no, el padre debe ser una parte "
                    + "de esa misma máquina.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Parte creada"),
            @ApiResponse(responseCode = "400",
                    description = "Datos inválidos (VALIDATION_ERROR), padre inexistente (PARENT_PART_NOT_FOUND) "
                            + "o de otra máquina (PARENT_PART_OTHER_MACHINE)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = MACHINE_404,
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PartResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable String machineId,
            @RequestBody PartRequest request) {
        Part created = service.create(AuthenticatedRole.from(jwt), machineId, request.toCommand());
        return ResponseEntity.created(URI.create("/parts/" + created.id())).body(PartResponse.from(created));
    }

    @PatchMapping("/parts/{id}")
    @Operation(summary = "Cambia el nombre de una parte",
            description = "Solo cambia el nombre. La parte no se mueve: un machineId o parentId distinto del "
                    + "actual se rechaza.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Datos inválidos (VALIDATION_ERROR), incluido un "
                    + "intento de mover la parte",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = PART_404,
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public PartResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestBody PartPatchRequest request) {
        return PartResponse.from(service.rename(AuthenticatedRole.from(jwt), id, request.toCommand()));
    }

    @DeleteMapping("/parts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Da de baja una parte",
            description = "Sin cascada: una parte con sub-partes no se elimina; un subárbol se elimina de las "
                    + "hojas hacia arriba.")
    @ApiResponses({
            @ApiResponse(responseCode = "404", description = PART_404,
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "La parte tiene sub-partes (PART_HAS_CHILDREN)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        service.delete(AuthenticatedRole.from(jwt), id);
    }
}
