package com.enterpriselab.api.maintenance.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de membresías, paquete-privado (ver {@link TechnicianJpaRepository}). */
interface TeamMemberJpaRepository extends JpaRepository<TeamMemberEntity, Long> {

    /**
     * {@code flushAutomatically}: vuelca lo pendiente antes de borrar, y
     * {@code clearAutomatically}: descarta lo cargado después, porque el
     * {@code DELETE} masivo no pasa por el contexto de persistencia. Es lo que
     * permite que el reemplazo de miembros ejecute los {@code DELETE} antes que
     * los {@code INSERT} (con {@code orphanRemoval} Hibernate los invierte y
     * choca con {@code team_members_team_technician_key}).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from TeamMemberEntity m where m.teamId = :teamId")
    void deleteByTeamId(@Param("teamId") Long teamId);

    /**
     * Pares {@code (teamId, legajo)} de los equipos pedidos, en el orden en que
     * se guardaron los miembros. Una sola consulta para todos los equipos: sin N+1.
     */
    @Query("select m.teamId, tc.legajo from TeamMemberEntity m "
            + "join TechnicianEntity tc on tc.id = m.technicianId "
            + "where m.teamId in :teamIds order by m.teamId, m.sortOrder")
    List<Object[]> findMembers(@Param("teamIds") Collection<Long> teamIds);
}
