package com.enterpriselab.api.maintenance.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data — detalle de infraestructura, paquete-privado: fuera
 * de {@code persistence} solo se conoce el puerto
 * {@link com.enterpriselab.api.maintenance.domain.TechnicianRepository}.
 */
interface TechnicianJpaRepository extends JpaRepository<TechnicianEntity, Long> {

    List<TechnicianEntity> findAllByOrderByIdAsc();

    Optional<TechnicianEntity> findByLegajo(String legajo);

    boolean existsByLegajo(String legajo);

    List<TechnicianEntity> findByLegajoIn(Collection<String> legajos);

    /**
     * Nativa a propósito: consultar {@code UserEntity} desde acá haría que
     * {@code maintenance.persistence} importe {@code auth.persistence}, y
     * {@code auth} ya importa esta entity (ciclo entre módulos).
     */
    @Query(value = "select exists (select 1 from users u join technicians t on t.id = u.technician_id "
            + "where t.legajo = :legajo)", nativeQuery = true)
    boolean existsLoginUserByLegajo(@Param("legajo") String legajo);
}
