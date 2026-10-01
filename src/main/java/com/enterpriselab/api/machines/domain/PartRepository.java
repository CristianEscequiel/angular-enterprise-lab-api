package com.enterpriselab.api.machines.domain;

import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de partes; la implementación JPA vive en {@code persistence}. */
public interface PartRepository {

    /** Todas las partes de la máquina, como lista plana, por id ascendente (orden de creación). */
    List<Part> findByMachineId(long machineId);

    Optional<Part> findById(long id);

    /** Alta (id nulo) o cambio de nombre: el resto de la parte no cambia nunca. */
    Part save(Part part);

    int countChildren(long partId);

    void deleteById(long id);
}
