package com.enterpriselab.api.workorders.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Repositorio Spring Data — detalle de infraestructura, paquete-privado: fuera de
 * {@code persistence} solo se conoce el puerto
 * {@link com.enterpriselab.api.workorders.domain.WorkOrderRepository}.
 */
interface WorkOrderJpaRepository
        extends JpaRepository<WorkOrderEntity, Long>, JpaSpecificationExecutor<WorkOrderEntity> {
}
