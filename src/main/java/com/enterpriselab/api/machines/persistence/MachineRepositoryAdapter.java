package com.enterpriselab.api.machines.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.enterpriselab.api.machines.domain.Machine;
import com.enterpriselab.api.machines.domain.MachineRepository;
import com.enterpriselab.api.shared.domain.ConflictException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.persistence.ConstraintViolations;

/**
 * Adaptador JPA del puerto {@link MachineRepository}. Las verificaciones del
 * servicio (código repetido, máquina con partes) son previas y pueden quedar
 * viejas: acá se hace {@code flush} dentro del método para que la base tenga la
 * última palabra y la violación se traduzca por nombre de constraint a la
 * misma excepción de dominio (design.md §6).
 */
@Repository
class MachineRepositoryAdapter implements MachineRepository {

    static final String CODE_UNIQUE = "machines_code_key";
    static final String PART_MACHINE_FK = "parts_machine_id_fkey";

    private final MachineJpaRepository jpaRepository;
    private final PartJpaRepository partJpaRepository;

    MachineRepositoryAdapter(MachineJpaRepository jpaRepository, PartJpaRepository partJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.partJpaRepository = partJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Machine> findAll() {
        return jpaRepository.findAllWithPartCount().stream().map(MachineMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Machine> findById(long id) {
        return jpaRepository.findWithPartCountById(id).map(MachineMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        return jpaRepository.existsByCode(code);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCodeAndIdNot(String code, long id) {
        return jpaRepository.existsByCodeAndIdNot(code, id);
    }

    @Override
    @Transactional
    public Machine save(Machine machine) {
        try {
            MachineEntity entity;
            if (machine.id() == null) {
                entity = MachineMapper.toNewEntity(machine);
            } else {
                entity = jpaRepository.findById(machine.id()).orElseThrow(() ->
                        new NotFoundException("No existe la máquina con id " + machine.id()));
                entity.update(machine.code(), machine.name());
            }
            entity = jpaRepository.saveAndFlush(entity);
            int partCount = Math.toIntExact(partJpaRepository.countByMachineId(entity.getId()));
            return new Machine(entity.getId(), entity.getCode(), entity.getName(), partCount);
        } catch (DataIntegrityViolationException violation) {
            if (ConstraintViolations.nameOf(violation).filter(CODE_UNIQUE::equals).isPresent()) {
                throw new ConflictException("DUPLICATE_MACHINE_CODE",
                        "Ya existe una máquina con código " + machine.code());
            }
            throw violation;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int countParts(long machineId) {
        return Math.toIntExact(partJpaRepository.countByMachineId(machineId));
    }

    @Override
    @Transactional
    public void deleteById(long id) {
        try {
            jpaRepository.deleteById(id);
            jpaRepository.flush();
        } catch (DataIntegrityViolationException violation) {
            if (ConstraintViolations.nameOf(violation).filter(PART_MACHINE_FK::equals).isPresent()) {
                throw new ConflictException("MACHINE_HAS_PARTS",
                        "La máquina no se puede eliminar: tiene partes");
            }
            throw violation;
        }
    }
}
