package com.enterpriselab.api.maintenance.domain;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.shared.domain.InvalidReferenceException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.domain.ValidationFailedException;

/**
 * Alta, consulta, edición y baja de equipos. Mismo orden de evaluación que
 * {@link TechnicianService} (design.md §4.1): rol → validación del cuerpo
 * (todos los errores juntos) → existencia del equipo → existencia de los
 * técnicos referenciados. {@code create} y {@code update} comparten el mismo
 * validador de {@code memberLegajos} (REQ-26, REQ-27, REQ-28 y REQ-31).
 */
@Service
public class TeamService {

    static final int MAX_NAME_LENGTH = 100;
    static final String REQUIRED_MESSAGE = "Es obligatorio";
    static final String NAME_TOO_LONG_MESSAGE = "Debe tener como máximo " + MAX_NAME_LENGTH + " caracteres";
    static final String MEMBER_FORMAT_MESSAGE = "Cada legajo debe tener entre 1 y 8 dígitos";
    static final String MEMBER_DUPLICATED_MESSAGE = "No puede haber legajos repetidos";
    static final String UNKNOWN_TECHNICIAN = "UNKNOWN_TECHNICIAN";

    private final TeamRepository teams;
    private final TechnicianRepository technicians;

    public TeamService(TeamRepository teams, TechnicianRepository technicians) {
        this.teams = teams;
        this.technicians = technicians;
    }

    public List<Team> list(Role actor) {
        MaintenancePermissions.requireTeams(actor);
        return teams.findAll();
    }

    public Team get(Role actor, String id) {
        MaintenancePermissions.requireTeams(actor);
        return find(id);
    }

    public Team create(Role actor, TeamCommand command) {
        MaintenancePermissions.requireTeams(actor);

        ValidTeam valid = validate(command);
        requireExistingTechnicians(valid.memberLegajos());
        return teams.save(new Team(null, valid.name(), valid.type(), valid.memberLegajos()));
    }

    /** Reemplaza nombre, tipo y miembros: todo o nada, porque {@code save} corre al final. */
    public Team update(Role actor, String id, TeamCommand command) {
        MaintenancePermissions.requireTeams(actor);

        ValidTeam valid = validate(command);
        Team existing = find(id);
        requireExistingTechnicians(valid.memberLegajos());
        return teams.save(new Team(existing.id(), valid.name(), valid.type(), valid.memberLegajos()));
    }

    public void delete(Role actor, String id) {
        MaintenancePermissions.requireTeams(actor);
        Team existing = find(id);
        teams.deleteById(existing.id());
    }

    /**
     * Un id que no es un número entero se trata como un equipo que no existe
     * (REQ-41). Se aceptan solo dígitos, y hasta 18 para no desbordar un
     * {@code long}.
     */
    private Team find(String id) {
        if (id == null || !id.matches("[0-9]{1,18}")) {
            throw notFound(id);
        }
        return teams.findById(Long.parseLong(id)).orElseThrow(() -> notFound(id));
    }

    private static NotFoundException notFound(String id) {
        return new NotFoundException("No existe el equipo con id " + id);
    }

    /** Valida la forma de todo el comando, sin cortar en el primer error, y devuelve los datos normalizados. */
    private static ValidTeam validate(TeamCommand command) {
        Map<String, String> errors = new LinkedHashMap<>();

        String name = command.name() == null ? null : command.name().strip();
        if (name == null || name.isEmpty()) {
            errors.put("name", REQUIRED_MESSAGE);
        } else if (name.length() > MAX_NAME_LENGTH) {
            errors.put("name", NAME_TOO_LONG_MESSAGE);
        }

        TeamType type = null;
        if (command.type() == null) {
            errors.put("type", REQUIRED_MESSAGE);
        } else {
            try {
                type = TeamType.fromValue(command.type());
            } catch (IllegalArgumentException invalid) {
                errors.put("type", "Valor inválido. Valores permitidos: " + Arrays.stream(TeamType.values())
                        .map(TeamType::toValue).collect(Collectors.joining(", ")));
            }
        }

        validateMembers(command.memberLegajos(), errors);

        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return new ValidTeam(name, type, command.memberLegajos());
    }

    /** REQ-26 (formato), REQ-28 (repetidos) y REQ-40 (lista obligatoria; vacía es válida). */
    private static void validateMembers(List<String> members, Map<String, String> errors) {
        if (members == null) {
            errors.put("memberLegajos", REQUIRED_MESSAGE);
            return;
        }
        if (!members.stream().allMatch(Legajo::isValid)) {
            errors.put("memberLegajos", MEMBER_FORMAT_MESSAGE);
        } else if (new HashSet<>(members).size() != members.size()) {
            errors.put("memberLegajos", MEMBER_DUPLICATED_MESSAGE);
        }
    }

    /** REQ-27: los legajos que no pertenecen a ningún técnico, todos juntos y en el orden recibido. */
    private void requireExistingTechnicians(List<String> members) {
        if (members.isEmpty()) {
            return;
        }
        Set<String> existing = technicians.findExistingLegajos(members);
        List<String> missing = members.stream().filter(legajo -> !existing.contains(legajo)).toList();
        if (missing.size() == 1) {
            throw new InvalidReferenceException(UNKNOWN_TECHNICIAN,
                    "No existe el técnico con legajo " + missing.get(0));
        }
        if (!missing.isEmpty()) {
            throw new InvalidReferenceException(UNKNOWN_TECHNICIAN,
                    "No existen técnicos con legajo: " + String.join(", ", missing));
        }
    }

    private record ValidTeam(String name, TeamType type, List<String> memberLegajos) {
    }
}
