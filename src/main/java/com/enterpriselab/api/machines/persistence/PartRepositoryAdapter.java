package com.enterpriselab.api.machines.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.enterpriselab.api.machines.domain.Part;
import com.enterpriselab.api.machines.domain.PartRepository;
import com.enterpriselab.api.shared.domain.ConflictException;
import com.enterpriselab.api.shared.domain.InvalidReferenceException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.persistence.ConstraintViolations;

/**
 * Adaptador JPA del puerto {@link PartRepository}. La FK compuesta
 * {@code parts_parent_same_machine_fkey} significa cosas distintas según la
 * operación (design.md §6): al insertar, el padre ya no existe; al borrar, la
 * parte tiene hijos.
 */
@Repository
class PartRepositoryAdapter implements PartRepository {

    static final String MACHINE_FK = "parts_machine_id_fkey";
    static final String PARENT_FK = "parts_parent_same_machine_fkey";

    private final PartJpaRepository jpaRepository;

    PartRepositoryAdapter(PartJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Part> findByMachineId(long machineId) {
        return jpaRepository.findByMachineIdOrderByIdAsc(machineId).stream().map(PartMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Part> findById(long id) {
        return jpaRepository.findById(id).map(PartMapper::toDomain);
    }

    @Override
    @Transactional
    public Part save(Part part) {
        try {
            PartEntity entity;
            if (part.id() == null) {
                entity = PartMapper.toNewEntity(part);
            } else {
                entity = jpaRepository.findById(part.id()).orElseThrow(() ->
                        new NotFoundException("No existe la parte con id " + part.id()));
                entity.rename(part.name());
            }
            return PartMapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException violation) {
            String constraint = ConstraintViolations.nameOf(violation).orElse("");
            if (MACHINE_FK.equals(constraint)) {
                throw new NotFoundException("No existe la máquina con id " + part.machineId());
            }
            if (PARENT_FK.equals(constraint)) {
                throw new InvalidReferenceException("PARENT_PART_NOT_FOUND",
                        "No existe la parte padre con id " + part.parentId());
            }
            throw violation;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int countChildren(long partId) {
        return Math.toIntExact(jpaRepository.countByParentId(partId));
    }

    @Override
    @Transactional
    public void deleteById(long id) {
        try {
            jpaRepository.deleteById(id);
            jpaRepository.flush();
        } catch (DataIntegrityViolationException violation) {
            if (ConstraintViolations.nameOf(violation).filter(PARENT_FK::equals).isPresent()) {
                throw new ConflictException("PART_HAS_CHILDREN",
                        "La parte no se puede eliminar: tiene sub-partes");
            }
            throw violation;
        }
    }
}
