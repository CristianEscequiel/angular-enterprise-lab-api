package com.enterpriselab.api.machines.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import org.springframework.stereotype.Service;

import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.shared.domain.ConflictException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.domain.NumericId;
import com.enterpriselab.api.shared.domain.ValidationFailedException;

/**
 * Alta, consulta, edición y baja de máquinas. Orden de evaluación (design.md
 * §4.1, REQ-36): rol → validación del cuerpo (todos los errores juntos) →
 * existencia de la máquina → duplicado o dependientes.
 */
@Service
public class MachineService {

    static final String DUPLICATE_MACHINE_CODE = "DUPLICATE_MACHINE_CODE";
    static final String MACHINE_HAS_PARTS = "MACHINE_HAS_PARTS";
    static final String REQUIRED_MESSAGE = "Es obligatorio";

    private final MachineRepository machines;

    public MachineService(MachineRepository machines) {
        this.machines = machines;
    }

    public List<Machine> list(Role actor) {
        MachinesPermissions.requireRead(actor);
        return machines.findAll();
    }

    public Machine get(Role actor, String id) {
        MachinesPermissions.requireRead(actor);
        return find(id);
    }

    public Machine create(Role actor, MachineCommand command) {
        MachinesPermissions.requireWrite(actor);

        ValidMachine valid = validate(command);
        if (machines.existsByCode(valid.code())) {
            throw duplicate(valid.code());
        }
        return machines.save(new Machine(null, valid.code(), valid.name(), 0));
    }

    public Machine update(Role actor, String id, MachineCommand command) {
        MachinesPermissions.requireWrite(actor);

        ValidMachine valid = validate(command);
        Machine existing = find(id);
        if (machines.existsByCodeAndIdNot(valid.code(), existing.id())) {
            throw duplicate(valid.code());
        }
        return machines.save(new Machine(existing.id(), valid.code(), valid.name(), existing.partCount()));
    }

    public void delete(Role actor, String id) {
        MachinesPermissions.requireWrite(actor);

        Machine existing = find(id);
        int parts = machines.countParts(existing.id());
        if (parts > 0) {
            throw new ConflictException(MACHINE_HAS_PARTS, "La máquina " + existing.code()
                    + " no se puede eliminar: tiene " + NameRules.plural(parts, "parte", "partes"));
        }
        machines.deleteById(existing.id());
    }

    private Machine find(String id) {
        OptionalLong parsed = NumericId.parse(id);
        if (parsed.isEmpty()) {
            throw notFound(id);
        }
        return machines.findById(parsed.getAsLong()).orElseThrow(() -> notFound(id));
    }

    private static NotFoundException notFound(String id) {
        return new NotFoundException("No existe la máquina con id " + id);
    }

    private static ConflictException duplicate(String code) {
        return new ConflictException(DUPLICATE_MACHINE_CODE, "Ya existe una máquina con código " + code);
    }

    /** Valida la forma de todo el comando, sin cortar en el primer error, y devuelve los datos normalizados. */
    private static ValidMachine validate(MachineCommand command) {
        Map<String, String> errors = new LinkedHashMap<>();

        String code = null;
        if (command.code() == null) {
            errors.put("code", REQUIRED_MESSAGE);
        } else if (!MachineCode.isValid(command.code())) {
            errors.put("code", MachineCode.INVALID_MESSAGE);
        } else {
            code = MachineCode.normalize(command.code());
        }

        String name = NameRules.validate(command.name(), errors);

        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return new ValidMachine(code, name);
    }

    private record ValidMachine(String code, String name) {
    }
}
