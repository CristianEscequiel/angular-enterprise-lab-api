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
}
