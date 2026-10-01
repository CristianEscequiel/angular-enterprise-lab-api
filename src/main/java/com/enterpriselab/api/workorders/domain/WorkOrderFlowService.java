package com.enterpriselab.api.workorders.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.enterpriselab.api.auth.domain.AuthService;
import com.enterpriselab.api.auth.domain.User;
import com.enterpriselab.api.maintenance.domain.TeamType;
import com.enterpriselab.api.shared.domain.ConflictException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.domain.NumericId;
import com.enterpriselab.api.shared.domain.ValidationFailedException;

/**
 * Tomar, cerrar y liberar una orden (spec 04). Orden de evaluación (design.md
 * §4.1): usuario ({@code 401}) → rol ({@code 403}) → formato del cuerpo
 * ({@code 400}) → existencia ({@code 404}) → equipo ({@code 403}) → estado y dueño
 * ({@code 409}) → actualización condicional. Si esta toca 0 filas, otra operación
 * ganó: se relee y se decide de nuevo, hasta {@value #MAX_ATTEMPTS} intentos.
 */
@Service
public class WorkOrderFlowService {

    static final String NOT_PENDING = "WORK_ORDER_NOT_PENDING";
    static final String NOT_IN_PROGRESS = "WORK_ORDER_NOT_IN_PROGRESS";
    static final String TAKEN_BY_OTHER = "WORK_ORDER_TAKEN_BY_OTHER";

    static final int COMMENT_MIN = 50;
    static final int COMMENT_MAX = 500;
    static final int MAX_ATTEMPTS = 3;
    static final String REQUIRED_MESSAGE = "Es obligatorio";

    private final AuthService auth;
    private final WorkOrderRepository orders;
    private final Clock clock;

    public WorkOrderFlowService(AuthService auth, WorkOrderRepository orders, Clock clock) {
        this.auth = auth;
        this.orders = orders;
        this.clock = clock;
    }

    public WorkOrder take(String username, String id) {
        User user = auth.currentUser(username);
        WorkOrderPermissions.requireTake(user.role());

        TeamType team = teamOf(user);
        return attempt(id, order -> {
            WorkOrderPermissions.requireTeamServes(team, order.type());
            if (order.status() != WorkOrderStatus.PENDING) {
                throw conflict(NOT_PENDING, "La orden " + order.id() + " no está pendiente", order);
            }
            return orders.take(order.id(), new TakenBy(user.id(), user.displayName(), now()));
        });
    }

    public WorkOrder close(String username, String id, CloseWorkOrderCommand command) {
        User user = auth.currentUser(username);
        WorkOrderPermissions.requireClose(user.role());

        Map<String, String> errors = new LinkedHashMap<>();
        WorkOrderStatus outcome = validateOutcome(command.outcome(), errors);
        String comment = validateComment(command.comment(), errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }

        TeamType team = teamOf(user);
        return attempt(id, order -> {
            WorkOrderPermissions.requireTeamServes(team, order.type());
            if (order.status() != WorkOrderStatus.IN_PROGRESS) {
                throw conflict(NOT_IN_PROGRESS, "La orden " + order.id() + " no está en progreso", order);
            }
            if (order.takenBy() == null || order.takenBy().userId() != user.id()) {
                throw conflict(TAKEN_BY_OTHER, "La orden " + order.id() + " la tiene otro técnico", order);
            }
            return orders.close(order.id(), user.id(), outcome,
                    new ClosingNote(comment, user.id(), user.displayName(), now()));
        });
    }

    public WorkOrder release(String username, String id) {
        User user = auth.currentUser(username);
        WorkOrderPermissions.requireRelease(user.role());

        return attempt(id, order -> {
            if (order.status() != WorkOrderStatus.IN_PROGRESS) {
                throw conflict(NOT_IN_PROGRESS, "La orden " + order.id() + " no está en progreso", order);
            }
            return orders.release(order.id());
        });
    }

    // --- Ayudas --------------------------------------------------------------------------------------

    /**
     * Lee, decide y aplica. Un resultado vacío significa que otra operación cambió la
     * orden entre la lectura y el {@code UPDATE}: se vuelve a leer (y puede ser un
     * {@code 404} si la borraron o un {@code 409} con el estado real).
     */
    private WorkOrder attempt(String id, Function<WorkOrder, Optional<WorkOrder>> transition) {
        OptionalLong parsed = NumericId.parse(id);
        if (parsed.isEmpty()) {
            throw notFound(id);
        }
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            WorkOrder order = orders.findById(parsed.getAsLong()).orElseThrow(() -> notFound(id));
            Optional<WorkOrder> result = transition.apply(order);
            if (result.isPresent()) {
                return result.get();
            }
        }
        throw new IllegalStateException(
                "No se pudo aplicar la transición de la orden " + id + " tras " + MAX_ATTEMPTS + " intentos");
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static TeamType teamOf(User user) {
        return user.teamType() == null ? null : TeamType.fromValue(user.teamType());
    }

    private static NotFoundException notFound(String id) {
        return new NotFoundException("No existe la orden con id " + id);
    }

    /** {@code details}: el estado real y, si hay dueño, quién es. */
    private static ConflictException conflict(String code, String message, WorkOrder order) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("status", order.status().toValue());
        if (order.takenBy() != null) {
            details.put("takenById", String.valueOf(order.takenBy().userId()));
            details.put("takenByName", order.takenBy().name());
        }
        return new ConflictException(code, message, details);
    }

    private static WorkOrderStatus validateOutcome(String raw, Map<String, String> errors) {
        if (raw == null) {
            errors.put("outcome", REQUIRED_MESSAGE);
            return null;
        }
        if (raw.equals(WorkOrderStatus.COMPLETED.toValue())) {
            return WorkOrderStatus.COMPLETED;
        }
        if (raw.equals(WorkOrderStatus.CANCELLED.toValue())) {
            return WorkOrderStatus.CANCELLED;
        }
        errors.put("outcome", "Valor inválido. Valores permitidos: completed, cancelled");
        return null;
    }

    private static String validateComment(String raw, Map<String, String> errors) {
        String stripped = raw == null ? null : raw.strip();
        if (stripped == null || stripped.isEmpty()) {
            errors.put("comment", REQUIRED_MESSAGE);
            return null;
        }
        if (stripped.length() < COMMENT_MIN || stripped.length() > COMMENT_MAX) {
            errors.put("comment", "Debe tener entre " + COMMENT_MIN + " y " + COMMENT_MAX + " caracteres");
            return null;
        }
        return stripped;
    }
}
