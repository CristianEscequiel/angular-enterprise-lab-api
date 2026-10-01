package com.enterpriselab.api.machines.domain;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de máquinas; la implementación JPA vive en {@code persistence}. */
public interface MachineRepository {

    /** Todas, con su {@code partCount}, por id ascendente (orden de alta). */
    List<Machine> findAll();

    Optional<Machine> findById(long id);

    boolean existsById(long id);

    /** {@code code} ya normalizado. */
    boolean existsByCode(String code);

    /** Para la edición: la máquina no es duplicada de sí misma (REQ-10). */
    boolean existsByCodeAndIdNot(String code, long id);

    /** Alta (id nulo) o edición de {@code code} y {@code name}. Devuelve la máquina con su {@code partCount}. */
    Machine save(Machine machine);

    int countParts(long machineId);

    void deleteById(long id);
}
