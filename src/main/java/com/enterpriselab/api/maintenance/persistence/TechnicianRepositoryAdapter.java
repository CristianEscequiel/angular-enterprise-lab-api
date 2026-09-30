package com.enterpriselab.api.maintenance.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.enterpriselab.api.maintenance.domain.Technician;
import com.enterpriselab.api.maintenance.domain.TechnicianRepository;
import com.enterpriselab.api.shared.domain.ConflictException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.persistence.ConstraintViolations;

/**
 * Adaptador JPA del puerto {@link TechnicianRepository}. Las verificaciones del
 * servicio (legajo repetido, técnico en uso) son previas y pueden quedar viejas
 * entre el chequeo y la escritura: acá se hace {@code flush} dentro del método
 * para que la base tenga la última palabra y la violación se traduzca por
 * nombre de constraint a la misma excepción de dominio (design.md §6).
 */
@Repository
class TechnicianRepositoryAdapter implements TechnicianRepository {

    static final String LEGAJO_UNIQUE = "technicians_legajo_key";
    static final Set<String> IN_USE_CONSTRAINTS = Set.of(
            "users_technician_id_fkey", "team_members_technician_id_fkey");

    private final TechnicianJpaRepository jpaRepository;

    TechnicianRepositoryAdapter(TechnicianJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Technician> findAll() {
        return jpaRepository.findAllByOrderByIdAsc().stream().map(TechnicianMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Technician> findByLegajo(String legajo) {
        return jpaRepository.findByLegajo(legajo).map(TechnicianMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByLegajo(String legajo) {
        return jpaRepository.existsByLegajo(legajo);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findExistingLegajos(Collection<String> legajos) {
        if (legajos.isEmpty()) {
            return Set.of();
        }
        return jpaRepository.findByLegajoIn(legajos).stream()
                .map(TechnicianEntity::getLegajo)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasLoginUser(String legajo) {
        return jpaRepository.existsLoginUserByLegajo(legajo);
    }

    @Override
    @Transactional
    public Technician save(Technician technician) {
        try {
            TechnicianEntity entity;
            if (technician.id() == null) {
                entity = TechnicianMapper.toNewEntity(technician);
            } else {
                entity = jpaRepository.findById(technician.id()).orElseThrow(() ->
                        new NotFoundException("No existe el técnico con legajo " + technician.legajo()));
                entity.updateProfile(technician.firstName(), technician.lastName(),
                        technician.specialty().toValue(), technician.teamType().toValue());
            }
            return TechnicianMapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException violation) {
            if (ConstraintViolations.nameOf(violation).filter(LEGAJO_UNIQUE::equals).isPresent()) {
                throw new ConflictException("DUPLICATE_LEGAJO",
                        "Ya existe un técnico con legajo " + technician.legajo());
            }
            throw violation;
        }
    }

    @Override
    @Transactional
    public void deleteByLegajo(String legajo) {
        try {
            jpaRepository.findByLegajo(legajo).ifPresent(jpaRepository::delete);
            jpaRepository.flush();
        } catch (DataIntegrityViolationException violation) {
            if (ConstraintViolations.nameOf(violation).filter(IN_USE_CONSTRAINTS::contains).isPresent()) {
                throw new ConflictException("TECHNICIAN_IN_USE", "El técnico " + legajo
                        + " no se puede eliminar: tiene un usuario de acceso o es miembro de un equipo");
            }
            throw violation;
        }
    }
}
