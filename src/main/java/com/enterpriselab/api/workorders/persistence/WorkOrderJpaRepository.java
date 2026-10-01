package com.enterpriselab.api.workorders.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * Repositorio Spring Data — detalle de infraestructura, paquete-privado: fuera de
 * {@code persistence} solo se conoce el puerto
 * {@link com.enterpriselab.api.workorders.domain.WorkOrderRepository}.
 */
interface WorkOrderJpaRepository
        extends JpaRepository<WorkOrderEntity, Long>, JpaSpecificationExecutor<WorkOrderEntity> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WorkOrderEntity o set o.status = 'in-progress', o.takenById = :userId, "
            + "o.takenByName = :name, o.takenAt = :at where o.id = :id and o.status = 'pending'")
    int take(@Param("id") long id, @Param("userId") long userId, @Param("name") String name,
            @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WorkOrderEntity o set o.status = :outcome, o.closingComment = :comment, "
            + "o.closingAuthorId = :userId, o.closingAuthorName = :name, o.closedAt = :at "
            + "where o.id = :id and o.status = 'in-progress' and o.takenById = :userId")
    int close(@Param("id") long id, @Param("userId") long userId, @Param("outcome") String outcome,
            @Param("comment") String comment, @Param("name") String name, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WorkOrderEntity o set o.status = 'pending', o.takenById = null, "
            + "o.takenByName = null, o.takenAt = null where o.id = :id and o.status = 'in-progress'")
    int release(@Param("id") long id);
}
