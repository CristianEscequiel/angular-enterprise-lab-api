package com.enterpriselab.api.workorders.domain;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.shared.domain.InvalidReferenceException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.domain.NumericId;
import com.enterpriselab.api.shared.domain.PageQuery;
import com.enterpriselab.api.shared.domain.PageResult;
import com.enterpriselab.api.shared.domain.ValidationFailedException;

/**
 * Listado, consulta, alta, edición y baja de órdenes. Orden de evaluación
 * (design.md §4.1, REQ-46): rol (en el alta, también el tipo si es válido) →
 * formato de los campos y parámetros, todos los errores juntos → existencia de la
 * orden de la URL → referencias (máquina, parte, intento de cambiar tipo o
 * máquina). El servidor es la autoridad: fija el estado inicial, el
 * {@code createdAt} y el {@code breadcrumb}.
 */
@Service
public class WorkOrderService {

    static final String MACHINE_NOT_FOUND = "MACHINE_NOT_FOUND";
    static final String PART_NOT_FOUND = "PART_NOT_FOUND";
    static final String PART_OTHER_MACHINE = "PART_OTHER_MACHINE";

    static final int TITLE_MIN = 3;
    static final int TITLE_MAX = 150;
    static final int DESCRIPTION_MIN = 10;
    static final int DESCRIPTION_MAX = 2000;
    static final int COMMENT_MAX = 200;

    static final String REQUIRED_MESSAGE = "Es obligatorio";
    static final String BREADCRUMB_SEPARATOR = " > ";

    private final WorkOrderRepository orders;
    private final MachineDirectory directory;
    private final Clock clock;

    public WorkOrderService(WorkOrderRepository orders, MachineDirectory directory, Clock clock) {
        this.orders = orders;
        this.directory = directory;
        this.clock = clock;
    }

    // --- Lectura -------------------------------------------------------------------------------------

    public PageResult<WorkOrder> list(Role actor, WorkOrderQuery query) {
        WorkOrderPermissions.requireView(actor);

        Map<String, String> errors = new LinkedHashMap<>();
        Integer page = parseInteger(query.page(), "page", PageQuery.DEFAULT_PAGE, 1, Integer.MAX_VALUE,
                "Debe ser un entero mayor o igual que 1", errors);
        Integer size = parseInteger(query.size(), "size", PageQuery.DEFAULT_SIZE, 1, PageQuery.MAX_SIZE,
                "Debe ser un entero entre 1 y " + PageQuery.MAX_SIZE, errors);
        WorkOrderStatus status = parseFilter(query.status(), "status", WorkOrderStatus::fromValue,
                WorkOrderStatus.values(), WorkOrderStatus::toValue, errors);
        Priority priority = parseFilter(query.priority(), "priority", Priority::fromValue, Priority.values(),
                Priority::toValue, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        String title = query.title() == null ? null : query.title().strip();
        WorkOrderFilter filter = new WorkOrderFilter(title == null || title.isEmpty() ? null : title, status,
                priority);
        return orders.search(filter, new PageQuery(page, size));
    }

    public WorkOrder get(Role actor, String id) {
        WorkOrderPermissions.requireView(actor);
        return find(id);
    }

    // --- Alta ----------------------------------------------------------------------------------------

    public WorkOrder create(Role actor, CreateWorkOrderCommand command) {
        WorkOrderPermissions.requireCreateAny(actor);
        // El permiso depende del tipo: si es válido y el rol no lo crea, 403 antes que cualquier 400.
        WorkOrderType type = lenientType(command.type());
        if (type != null) {
            WorkOrderPermissions.requireCreate(actor, type);
        }

        Map<String, String> errors = new LinkedHashMap<>();
        String title = validateText(command.title(), "title", TITLE_MIN, TITLE_MAX, errors);
        String description = validateText(command.description(), "description", DESCRIPTION_MIN,
                DESCRIPTION_MAX, errors);
        if (command.type() == null) {
            errors.put("type", REQUIRED_MESSAGE);
        } else if (type == null) {
            errors.put("type", invalidValue(WorkOrderType.values(), WorkOrderType::toValue));
        }
        Priority priority = validatePriority(command.priority(), errors);
        if (!command.machineRefSent()) {
            errors.put("machineRef", REQUIRED_MESSAGE);
        } else if (command.machineId() == null) {
            errors.put("machineRef.machineId", REQUIRED_MESSAGE);
        }
        String comment = command.comment() == null ? "" : command.comment().strip();
        if (comment.length() > COMMENT_MAX) {
            errors.put("machineRef.comment", "Debe tener como máximo " + COMMENT_MAX + " caracteres");
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        MachineRef machineRef = resolveReference(command.machineId(), command.partId(), comment);
        WorkOrder order = new WorkOrder(null, title, description, machineRef, type, priority,
                WorkOrderStatus.PENDING, clock.instant().truncatedTo(ChronoUnit.MILLIS), null, null);
        return orders.save(order);
    }

    // --- Edición y baja ------------------------------------------------------------------------------

    /** Cambia solo título, descripción y prioridad (REQ-32); tipo y máquina no se editan (REQ-33, REQ-34). */
    public WorkOrder update(Role actor, String id, UpdateWorkOrderCommand command) {
        WorkOrderPermissions.requireEdit(actor);

        Map<String, String> errors = new LinkedHashMap<>();
        String title = validateText(command.title(), "title", TITLE_MIN, TITLE_MAX, errors);
        String description = validateText(command.description(), "description", DESCRIPTION_MIN,
                DESCRIPTION_MAX, errors);
        Priority priority = validatePriority(command.priority(), errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        WorkOrder existing = find(id);
        requireNotChanged(existing, command);
        return orders.save(new WorkOrder(existing.id(), title, description, existing.machineRef(), existing.type(),
                priority, existing.status(), existing.createdAt(), existing.takenBy(), existing.closingNote()));
    }

    public void delete(Role actor, String id) {
        WorkOrderPermissions.requireDelete(actor);
        WorkOrder existing = find(id);
        orders.deleteById(existing.id());
    }

    // --- Ayudas --------------------------------------------------------------------------------------

    private WorkOrder find(String id) {
        OptionalLong parsed = NumericId.parse(id);
        if (parsed.isEmpty()) {
            throw notFound(id);
        }
        return orders.findById(parsed.getAsLong()).orElseThrow(() -> notFound(id));
    }

    private static NotFoundException notFound(String id) {
        return new NotFoundException("No existe la orden con id " + id);
    }

    /** Un texto obligatorio, recortado y dentro de su rango de largo; anota el error y devuelve {@code null} si no. */
    private static String validateText(String raw, String field, int min, int max, Map<String, String> errors) {
        String stripped = raw == null ? null : raw.strip();
        if (stripped == null || stripped.isEmpty()) {
            errors.put(field, REQUIRED_MESSAGE);
            return null;
        }
        if (stripped.length() < min || stripped.length() > max) {
            errors.put(field, "Debe tener entre " + min + " y " + max + " caracteres");
            return null;
        }
        return stripped;
    }

    private static Priority validatePriority(String raw, Map<String, String> errors) {
        if (raw == null) {
            errors.put("priority", REQUIRED_MESSAGE);
            return null;
        }
        try {
            return Priority.fromValue(raw);
        } catch (IllegalArgumentException invalid) {
            errors.put("priority", invalidValue(Priority.values(), Priority::toValue));
            return null;
        }
    }

    private static WorkOrderType lenientType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return WorkOrderType.fromValue(raw);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static <E> String invalidValue(E[] values, Function<E, String> toValue) {
        return "Valor inválido. Valores permitidos: "
                + Arrays.stream(values).map(toValue).collect(Collectors.joining(", "));
    }

    /** {@code page} y {@code size}: ausente usa el valor por defecto; si no, un entero dentro del rango. */
    private static Integer parseInteger(String raw, String field, int defaultValue, int min, int max,
            String rangeMessage, Map<String, String> errors) {
        if (raw == null) {
            return defaultValue;
        }
        if (!raw.matches("-?[0-9]+")) {
            errors.put(field, "Debe ser un número entero");
            return null;
        }
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException overflow) {
            errors.put(field, rangeMessage);
            return null;
        }
        if (value < min || value > max) {
            errors.put(field, rangeMessage);
            return null;
        }
        return value;
    }

    /** Filtro por valor de un enum: ausente o vacío no filtra; si tiene valor debe ser uno válido (REQ-12). */
    private static <E> E parseFilter(String raw, String field, Function<String, E> parser,
            E[] values, Function<E, String> toValue, Map<String, String> errors) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return parser.apply(raw);
        } catch (IllegalArgumentException invalid) {
            errors.put(field, invalidValue(values, toValue));
            return null;
        }
    }

    /**
     * Máquina primero y después la parte (design.md §4.2): con las dos malas gana
     * {@code MACHINE_NOT_FOUND}. Un id vacío o no numérico es una referencia que no
     * resuelve, no un error de formato (REQ-22, REQ-25).
     */
    private MachineRef resolveReference(String machineIdText, String partIdText, String comment) {
        OptionalLong machineId = NumericId.parse(machineIdText);
        String machineName = machineId.isEmpty() ? null : directory.findMachineName(machineId.getAsLong()).orElse(null);
        if (machineName == null) {
            throw new InvalidReferenceException(MACHINE_NOT_FOUND,
                    "No existe la máquina con id " + machineIdText);
        }
        if (partIdText == null) {
            return new MachineRef(machineId.getAsLong(), null, machineName, comment);
        }

        OptionalLong partId = NumericId.parse(partIdText);
        PartLocation location = partId.isEmpty() ? null : directory.locatePart(partId.getAsLong()).orElse(null);
        if (location == null) {
            throw new InvalidReferenceException(PART_NOT_FOUND, "No existe la parte con id " + partIdText);
        }
        if (location.machineId() != machineId.getAsLong()) {
            throw new InvalidReferenceException(PART_OTHER_MACHINE,
                    "La parte " + partIdText + " pertenece a otra máquina");
        }
        String breadcrumb = machineName + BREADCRUMB_SEPARATOR
                + String.join(BREADCRUMB_SEPARATOR, location.pathNames());
        return new MachineRef(machineId.getAsLong(), partId.getAsLong(), breadcrumb, comment);
    }

    /** REQ-33 y REQ-34: un valor enviado y distinto del actual es un error; ausente o igual se ignora. */
    private static void requireNotChanged(WorkOrder existing, UpdateWorkOrderCommand command) {
        Map<String, String> attempts = new LinkedHashMap<>();
        MachineRef ref = existing.machineRef();
        if (command.type() != null && !command.type().equals(existing.type().toValue())) {
            attempts.put("type", "No se puede cambiar el tipo de una orden");
        }
        if (command.machineId() != null && !command.machineId().equals(String.valueOf(ref.machineId()))) {
            attempts.put("machineRef.machineId", "No se puede cambiar la máquina de una orden");
        }
        String currentPart = ref.partId() == null ? null : String.valueOf(ref.partId());
        if (command.partIdSent() && !Objects.equals(command.partId(), currentPart)) {
            attempts.put("machineRef.partId", "No se puede cambiar la parte de una orden");
        }
        if (command.comment() != null && !command.comment().strip().equals(ref.comment())) {
            attempts.put("machineRef.comment", "No se puede cambiar el comentario de la máquina de una orden");
        }
        if (!attempts.isEmpty()) {
            throw new ValidationFailedException(attempts);
        }
    }
}
