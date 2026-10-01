package com.enterpriselab.api.machines.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data de partes, paquete-privado (ver {@link MachineJpaRepository}). */
interface PartJpaRepository extends JpaRepository<PartEntity, Long> {

    List<PartEntity> findByMachineIdOrderByIdAsc(Long machineId);

    long countByMachineId(Long machineId);

    long countByParentId(Long parentId);
}
