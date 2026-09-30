package com.enterpriselab.api.maintenance.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.shared.domain.ConflictException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.domain.ValidationFailedException;

/**
 * Alta, consulta, edición y baja de técnicos. El orden de evaluación es el de
 * design.md §4.1: rol → formato del legajo de la URL y validación del cuerpo
 * (todos los errores juntos) → existencia → integridad. La autorización va
 * primero para que un rol sin permiso reciba {@code 403} aunque mande datos
 * inválidos.
 *
 * <p>Todavía sin {@code @Service}: necesita los adaptadores de
 * {@link TechnicianRepository} y {@link TeamRepository} (tareas 7 y 8), y
 * registrado como bean sin ellos el contexto de Spring no arranca. Se
 * anota en la tarea 8, cuando ya existen los dos.
 */
public class TechnicianService {

    static final int MAX_NAME_LENGTH = 100;
    static final String REQUIRED_MESSAGE = "Es obligatorio";
    static final String NAME_TOO_LONG_MESSAGE = "Debe tener como máximo " + MAX_NAME_LENGTH + " caracteres";
    static final String LEGAJO_IMMUTABLE_MESSAGE = "El legajo no se puede modificar";
    static final String DUPLICATE_LEGAJO = "DUPLICATE_LEGAJO";
    static final String TECHNICIAN_IN_USE = "TECHNICIAN_IN_USE";

    private final TechnicianRepository technicians;
    private final TeamRepository teams;

    public TechnicianService(TechnicianRepository technicians, TeamRepository teams) {
        this.technicians = technicians;
        this.teams = teams;
    }

    public List<Technician> list(Role actor) {
        MaintenancePermissions.requireTechniciansReadWrite(actor);
        return technicians.findAll();
    }

    public Technician get(Role actor, String legajo) {
        MaintenancePermissions.requireTechniciansReadWrite(actor);
        Legajo.require(legajo);
        return find(legajo);
    }

    public Technician create(Role actor, TechnicianCommand command) {
        MaintenancePermissions.requireTechniciansReadWrite(actor);

        Map<String, String> errors = new LinkedHashMap<>();
        if (!Legajo.isValid(command.legajo())) {
            errors.put("legajo", Legajo.INVALID_MESSAGE);
        }
        errors.putAll(validateFields(command));
        failIfAny(errors);

        if (technicians.existsByLegajo(command.legajo())) {
            throw new ConflictException(DUPLICATE_LEGAJO,
                    "Ya existe un técnico con legajo " + command.legajo());
        }
        return technicians.save(new Technician(null, command.legajo(), command.firstName().strip(),
                command.lastName().strip(), Specialty.fromValue(command.specialty()),
                TeamType.fromValue(command.teamType())));
    }

    public Technician update(Role actor, String legajo, TechnicianCommand command) {
        MaintenancePermissions.requireTechniciansReadWrite(actor);
        Legajo.require(legajo);

        Map<String, String> errors = new LinkedHashMap<>();
        if (command.legajo() != null && !command.legajo().equals(legajo)) {
            errors.put("legajo", LEGAJO_IMMUTABLE_MESSAGE);
        }
        errors.putAll(validateFields(command));
        failIfAny(errors);

        Technician existing = find(legajo);
        return technicians.save(new Technician(existing.id(), existing.legajo(), command.firstName().strip(),
                command.lastName().strip(), Specialty.fromValue(command.specialty()),
                TeamType.fromValue(command.teamType())));
    }

    public void delete(Role actor, String legajo) {
        MaintenancePermissions.requireTechniciansDelete(actor);
        Legajo.require(legajo);
        find(legajo);

        List<String> reasons = new ArrayList<>();
        if (technicians.hasLoginUser(legajo)) {
            reasons.add("tiene un usuario de acceso");
        }
        List<String> teamNames = teams.findNamesByMemberLegajo(legajo);
        if (!teamNames.isEmpty()) {
            reasons.add("es miembro de: " + String.join(", ", teamNames));
        }
        if (!reasons.isEmpty()) {
            throw new ConflictException(TECHNICIAN_IN_USE, "El técnico " + legajo
                    + " no se puede eliminar: " + String.join("; ", reasons));
        }

        technicians.deleteByLegajo(legajo);
    }

    private Technician find(String legajo) {
        return technicians.findByLegajo(legajo)
                .orElseThrow(() -> new NotFoundException("No existe el técnico con legajo " + legajo));
    }

    /** Valida nombre, apellido, especialidad y tipo de equipo, sin cortar en el primer error. */
    private static Map<String, String> validateFields(TechnicianCommand command) {
        Map<String, String> errors = new LinkedHashMap<>();
        validateName("firstName", command.firstName(), errors);
        validateName("lastName", command.lastName(), errors);
        validateEnum("specialty", command.specialty(), errors,
                () -> Specialty.fromValue(command.specialty()),
                Arrays.stream(Specialty.values()).map(Specialty::toValue).collect(Collectors.joining(", ")));
        validateEnum("teamType", command.teamType(), errors,
                () -> TeamType.fromValue(command.teamType()),
                Arrays.stream(TeamType.values()).map(TeamType::toValue).collect(Collectors.joining(", ")));
        return errors;
    }

    private static void validateName(String field, String value, Map<String, String> errors) {
        if (value == null || value.strip().isEmpty()) {
            errors.put(field, REQUIRED_MESSAGE);
        } else if (value.strip().length() > MAX_NAME_LENGTH) {
            errors.put(field, NAME_TOO_LONG_MESSAGE);
        }
    }

    private static void validateEnum(String field, String value, Map<String, String> errors,
            Runnable parse, String allowedValues) {
        if (value == null) {
            errors.put(field, REQUIRED_MESSAGE);
            return;
        }
        try {
            parse.run();
        } catch (IllegalArgumentException invalid) {
            errors.put(field, "Valor inválido. Valores permitidos: " + allowedValues);
        }
    }

    private static void failIfAny(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
    }
}
