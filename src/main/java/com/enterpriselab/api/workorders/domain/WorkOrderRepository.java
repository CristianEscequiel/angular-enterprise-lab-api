package com.enterpriselab.api.workorders.domain;

import java.util.Optional;

import com.enterpriselab.api.shared.domain.PageQuery;
import com.enterpriselab.api.shared.domain.PageResult;

/** Puerto de persistencia de órdenes; la implementación JPA vive en {@code persistence}. */
public interface WorkOrderRepository {

    /** La página pedida de las órdenes que cumplen el filtro, por {@code id} ascendente. */
    PageResult<WorkOrder> search(WorkOrderFilter filter, PageQuery page);

    Optional<WorkOrder> findById(long id);

    /** Alta (id nulo) o edición de título, descripción y prioridad: lo demás de la orden no cambia por esta vía. */
    WorkOrder save(WorkOrder order);

    void deleteById(long id);

    /** {@code pending} → {@code in-progress} con dueño; vacío si la orden ya no está {@code pending} (0 filas). */
    Optional<WorkOrder> take(long id, TakenBy owner);

    /** {@code in-progress} del dueño → {@code outcome} con nota; vacío si ya no cumple (0 filas). */
    Optional<WorkOrder> close(long id, long ownerId, WorkOrderStatus outcome, ClosingNote note);

    /** {@code in-progress} → {@code pending} sin dueño; vacío si ya no está {@code in-progress} (0 filas). */
    Optional<WorkOrder> release(long id);
}
