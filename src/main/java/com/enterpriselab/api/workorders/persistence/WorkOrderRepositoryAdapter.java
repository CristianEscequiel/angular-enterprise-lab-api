package com.enterpriselab.api.workorders.persistence;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.domain.PageQuery;
import com.enterpriselab.api.shared.domain.PageResult;
import com.enterpriselab.api.workorders.domain.WorkOrder;
import com.enterpriselab.api.workorders.domain.WorkOrderFilter;
import com.enterpriselab.api.workorders.domain.WorkOrderRepository;

/**
 * Adaptador JPA del puerto {@link WorkOrderRepository}. No traduce constraints: sin
 * clave foránea hacia máquinas ni partes ninguna escritura de esta spec puede
 * fallar por una referencia, y un {@code CHECK} violado es un error de
 * programación (el dominio valida antes) que debe llegar como {@code 500}
 * (design.md §6).
 */
@Repository
class WorkOrderRepositoryAdapter implements WorkOrderRepository {

    private final WorkOrderJpaRepository jpaRepository;

    WorkOrderRepositoryAdapter(WorkOrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /** Una consulta de datos y una de conteo; con {@code page} fuera de rango, {@code content} vacío y totales reales. */
    @Override
    @Transactional(readOnly = true)
    public PageResult<WorkOrder> search(WorkOrderFilter filter, PageQuery page) {
        Page<WorkOrderEntity> result = jpaRepository.findAll(WorkOrderSpecifications.matching(filter),
                PageRequest.of(page.page() - 1, page.size(), Sort.by("id")));
        return new PageResult<>(result.getContent().stream().map(WorkOrderMapper::toDomain).toList(),
                page.page(), page.size(), result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkOrder> findById(long id) {
        return jpaRepository.findById(id).map(WorkOrderMapper::toDomain);
    }

    @Override
    @Transactional
    public WorkOrder save(WorkOrder order) {
        WorkOrderEntity entity;
        if (order.id() == null) {
            entity = WorkOrderMapper.toNewEntity(order);
        } else {
            entity = jpaRepository.findById(order.id()).orElseThrow(() ->
                    new NotFoundException("No existe la orden con id " + order.id()));
            entity.updateDetails(order.title(), order.description(), order.priority().toValue());
        }
        return WorkOrderMapper.toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public void deleteById(long id) {
        jpaRepository.deleteById(id);
        jpaRepository.flush();
    }
}
