package com.enterpriselab.api.maintenance.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Puerto de persistencia de técnicos. La implementación JPA vive en
 * {@code maintenance/persistence}; {@code domain} no sabe nada de ella.
 */
public interface TechnicianRepository {

    /** Todos los técnicos, por orden de alta. */
    List<Technician> findAll();

    Optional<Technician> findByLegajo(String legajo);

    boolean existsByLegajo(String legajo);

    /** De los legajos recibidos, los que pertenecen a un técnico. */
    Set<String> findExistingLegajos(Collection<String> legajos);

    /** {@code true} si el técnico tiene un usuario de login asociado (REQ-15). */
    boolean hasLoginUser(String legajo);

    /** Alta (con {@code id} nulo) o edición. */
    Technician save(Technician technician);

    void deleteByLegajo(String legajo);
}
