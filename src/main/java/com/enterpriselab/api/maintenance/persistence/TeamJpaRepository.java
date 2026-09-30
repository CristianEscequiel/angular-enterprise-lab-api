package com.enterpriselab.api.maintenance.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de equipos, paquete-privado (ver {@link TechnicianJpaRepository}). */
interface TeamJpaRepository extends JpaRepository<TeamEntity, Long> {

    List<TeamEntity> findAllByOrderByIdAsc();

    /** Nombres de los equipos de los que el legajo es miembro (REQ-16). */
    @Query("select t.name from TeamEntity t "
            + "join TeamMemberEntity m on m.teamId = t.id "
            + "join TechnicianEntity tc on tc.id = m.technicianId "
            + "where tc.legajo = :legajo order by t.id")
    List<String> findNamesByMemberLegajo(@Param("legajo") String legajo);
}
