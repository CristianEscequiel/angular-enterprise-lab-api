package com.enterpriselab.api.maintenance.domain;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia de equipos. La implementación JPA vive en
 * {@code maintenance/persistence}. {@code TechnicianService} lo usa para saber
 * de qué equipos es miembro un técnico antes de darlo de baja (REQ-16).
 */
public interface TeamRepository {

    /** Todos los equipos, por orden de alta, con sus miembros en el orden en que se guardaron. */
    List<Team> findAll();

    Optional<Team> findById(long id);

    /** Alta (con {@code id} nulo) o reemplazo total de nombre, tipo y miembros. */
    Team save(Team team);

    void deleteById(long id);

    /** Nombres de los equipos de los que el legajo es miembro. */
    List<String> findNamesByMemberLegajo(String legajo);
}
