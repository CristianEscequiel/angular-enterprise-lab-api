package com.enterpriselab.api.maintenance.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.enterpriselab.api.maintenance.domain.Team;
import com.enterpriselab.api.maintenance.domain.TeamRepository;
import com.enterpriselab.api.shared.domain.InvalidReferenceException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.persistence.ConstraintViolations;

/**
 * Adaptador JPA del puerto {@link TeamRepository}. {@code save} reemplaza la
 * lista completa de miembros: borra las filas del equipo, hace {@code flush} y
 * recién ahí inserta las nuevas con {@code sort_order = índice}. Todo en una
 * transacción, así que un error deja el equipo como estaba (todo o nada).
 */
@Repository
class TeamRepositoryAdapter implements TeamRepository {

    static final String MEMBER_TECHNICIAN_FK = "team_members_technician_id_fkey";

    private final TeamJpaRepository teamJpa;
    private final TeamMemberJpaRepository memberJpa;
    private final TechnicianJpaRepository technicianJpa;

    TeamRepositoryAdapter(TeamJpaRepository teamJpa, TeamMemberJpaRepository memberJpa,
            TechnicianJpaRepository technicianJpa) {
        this.teamJpa = teamJpa;
        this.memberJpa = memberJpa;
        this.technicianJpa = technicianJpa;
    }

    /** Dos consultas para todos los equipos (equipos y miembros), sin N+1. */
    @Override
    @Transactional(readOnly = true)
    public List<Team> findAll() {
        List<TeamEntity> entities = teamJpa.findAllByOrderByIdAsc();
        Map<Long, List<String>> members = membersByTeam(entities);
        return entities.stream()
                .map(entity -> TeamMapper.toDomain(entity, members.getOrDefault(entity.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Team> findById(long id) {
        return teamJpa.findById(id).map(entity ->
                TeamMapper.toDomain(entity, membersByTeam(List.of(entity)).getOrDefault(id, List.of())));
    }

    @Override
    @Transactional
    public Team save(Team team) {
        try {
            TeamEntity entity;
            if (team.id() == null) {
                entity = teamJpa.saveAndFlush(TeamMapper.toNewEntity(team));
            } else {
                entity = teamJpa.findById(team.id()).orElseThrow(() ->
                        new NotFoundException("No existe el equipo con id " + team.id()));
                entity.update(team.name(), team.type().toValue());
                entity = teamJpa.saveAndFlush(entity);
            }

            memberJpa.deleteByTeamId(entity.getId());
            memberJpa.saveAll(memberRows(entity.getId(), team.memberLegajos()));
            memberJpa.flush();

            return new Team(entity.getId(), team.name(), team.type(), team.memberLegajos());
        } catch (DataIntegrityViolationException violation) {
            // Un técnico borrado entre la validación del servicio y este insert.
            if (ConstraintViolations.nameOf(violation).filter(MEMBER_TECHNICIAN_FK::equals).isPresent()) {
                throw new InvalidReferenceException("UNKNOWN_TECHNICIAN",
                        "Alguno de los técnicos indicados ya no existe");
            }
            throw violation;
        }
    }

    @Override
    @Transactional
    public void deleteById(long id) {
        // Las membresías caen por el ON DELETE CASCADE de team_members.team_id.
        teamJpa.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findNamesByMemberLegajo(String legajo) {
        return teamJpa.findNamesByMemberLegajo(legajo);
    }

    private Map<Long, List<String>> membersByTeam(List<TeamEntity> teams) {
        if (teams.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = teams.stream().map(TeamEntity::getId).toList();
        Map<Long, List<String>> members = new HashMap<>();
        for (Object[] row : memberJpa.findMembers(ids)) {
            members.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add((String) row[1]);
        }
        return members;
    }

    /** Un técnico que ya no existe se informa como referencia inválida, igual que el chequeo del servicio. */
    private List<TeamMemberEntity> memberRows(Long teamId, List<String> legajos) {
        if (legajos.isEmpty()) {
            return List.of();
        }
        Map<String, Long> technicianIds = technicianJpa.findByLegajoIn(legajos).stream()
                .collect(Collectors.toMap(TechnicianEntity::getLegajo, TechnicianEntity::getId));
        List<TeamMemberEntity> rows = new ArrayList<>();
        for (int index = 0; index < legajos.size(); index++) {
            Long technicianId = technicianIds.get(legajos.get(index));
            if (technicianId == null) {
                throw new InvalidReferenceException("UNKNOWN_TECHNICIAN",
                        "No existe el técnico con legajo " + legajos.get(index));
            }
            rows.add(new TeamMemberEntity(teamId, technicianId, index));
        }
        return rows;
    }
}
