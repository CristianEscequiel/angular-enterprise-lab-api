package com.enterpriselab.api.machines.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

import org.springframework.stereotype.Service;

import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.shared.domain.ConflictException;
import com.enterpriselab.api.shared.domain.InvalidReferenceException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.domain.ValidationFailedException;

/**
 * Alta, consulta, edición del nombre y baja de partes. Orden de evaluación
 * (design.md §4.1, REQ-36): rol → validación del cuerpo → existencia de la
 * máquina o de la parte de la URL → referencias (padre, "no se mueve") →
 * dependientes. La parte nunca se mueve, así que el árbol no puede tener ciclos
 * ni partes cruzadas entre máquinas.
 */
@Service
public class PartService {

    static final String PARENT_PART_NOT_FOUND = "PARENT_PART_NOT_FOUND";
    static final String PARENT_PART_OTHER_MACHINE = "PARENT_PART_OTHER_MACHINE";
    static final String PART_HAS_CHILDREN = "PART_HAS_CHILDREN";

    private final PartRepository parts;
    private final MachineRepository machines;

    public PartService(PartRepository parts, MachineRepository machines) {
        this.parts = parts;
        this.machines = machines;
    }

    public List<Part> listByMachine(Role actor, String machineId) {
        MachinesPermissions.requireRead(actor);
        return parts.findByMachineId(requireMachine(machineId));
    }

    public Part create(Role actor, String machineId, PartCommand command) {
        MachinesPermissions.requireWrite(actor);

        String name = validName(command.name());
        long machine = requireMachine(machineId);
        Long parentId = resolveParent(machine, command.parentId());
        return parts.save(new Part(null, machine, parentId, name));
    }

    /** Cambia solo el nombre (REQ-24); un {@code machineId} o {@code parentId} distinto del actual es un error (REQ-25). */
    public Part rename(Role actor, String id, PartPatchCommand command) {
        MachinesPermissions.requireWrite(actor);

        String name = validName(command.name());
        Part existing = find(id);
        requireNotMoved(existing, command);
        return parts.save(new Part(existing.id(), existing.machineId(), existing.parentId(), name));
    }

    public void delete(Role actor, String id) {
        MachinesPermissions.requireWrite(actor);

        Part existing = find(id);
        int children = parts.countChildren(existing.id());
        if (children > 0) {
            throw new ConflictException(PART_HAS_CHILDREN, "La parte «" + existing.name()
                    + "» no se puede eliminar: tiene " + NameRules.plural(children, "sub-parte", "sub-partes"));
        }
        parts.deleteById(existing.id());
    }

    private static String validName(String name) {
        Map<String, String> errors = new LinkedHashMap<>();
        String valid = NameRules.validate(name, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return valid;
    }

    private long requireMachine(String machineId) {
        OptionalLong parsed = MachineIds.parse(machineId);
        if (parsed.isEmpty() || !machines.existsById(parsed.getAsLong())) {
            throw new NotFoundException("No existe la máquina con id " + machineId);
        }
        return parsed.getAsLong();
    }

    private Part find(String id) {
        OptionalLong parsed = MachineIds.parse(id);
        if (parsed.isEmpty()) {
            throw notFound(id);
        }
        return parts.findById(parsed.getAsLong()).orElseThrow(() -> notFound(id));
    }

    private static NotFoundException notFound(String id) {
        return new NotFoundException("No existe la parte con id " + id);
    }

    /** {@code null} es primer nivel; cualquier otro valor debe ser una parte de esa misma máquina (REQ-21, REQ-22). */
    private Long resolveParent(long machineId, String parentId) {
        if (parentId == null) {
            return null;
        }
        OptionalLong parsed = MachineIds.parse(parentId);
        Part parent = parsed.isEmpty() ? null : parts.findById(parsed.getAsLong()).orElse(null);
        if (parent == null) {
            throw new InvalidReferenceException(PARENT_PART_NOT_FOUND, "No existe la parte padre con id " + parentId);
        }
        if (parent.machineId() != machineId) {
            throw new InvalidReferenceException(PARENT_PART_OTHER_MACHINE,
                    "La parte padre " + parentId + " pertenece a otra máquina");
        }
        return parent.id();
    }

    private static void requireNotMoved(Part existing, PartPatchCommand command) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (command.machineIdSent() && !Objects.equals(command.machineId(), String.valueOf(existing.machineId()))) {
            errors.put("machineId", "No se puede cambiar la máquina de una parte");
        }
        String currentParent = existing.parentId() == null ? null : String.valueOf(existing.parentId());
        if (command.parentIdSent() && !Objects.equals(command.parentId(), currentParent)) {
            errors.put("parentId", "No se puede cambiar el padre de una parte");
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
    }
}
