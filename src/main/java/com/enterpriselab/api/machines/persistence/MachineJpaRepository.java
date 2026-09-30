package com.enterpriselab.api.machines.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data — detalle de infraestructura, paquete-privado: fuera
 * de {@code persistence} solo se conoce el puerto
 * {@link com.enterpriselab.api.machines.domain.MachineRepository}.
 */
interface MachineJpaRepository extends JpaRepository<MachineEntity, Long> {

    String WITH_PART_COUNT = "select new com.enterpriselab.api.machines.persistence.MachineRow("
            + "m.id, m.code, m.name, count(p)) from MachineEntity m "
            + "left join PartEntity p on p.machineId = m.id ";
    String GROUP_BY = "group by m.id, m.code, m.name ";

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    /** Una sola consulta para todas las máquinas y su {@code partCount}. */
    @Query(WITH_PART_COUNT + GROUP_BY + "order by m.id")
    List<MachineRow> findAllWithPartCount();

    @Query(WITH_PART_COUNT + "where m.id = :id " + GROUP_BY)
    Optional<MachineRow> findWithPartCountById(@Param("id") Long id);
}
