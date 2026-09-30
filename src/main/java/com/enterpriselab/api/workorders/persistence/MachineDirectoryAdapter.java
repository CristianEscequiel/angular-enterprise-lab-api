package com.enterpriselab.api.workorders.persistence;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.enterpriselab.api.machines.domain.Machine;
import com.enterpriselab.api.machines.domain.MachineRepository;
import com.enterpriselab.api.machines.domain.Part;
import com.enterpriselab.api.machines.domain.PartRepository;
import com.enterpriselab.api.workorders.domain.MachineDirectory;
import com.enterpriselab.api.workorders.domain.PartLocation;

/**
 * Adaptador del puerto {@link MachineDirectory}. Se apoya en los puertos
 * <em>públicos</em> del módulo {@code machines} ({@link MachineRepository} y
 * {@link PartRepository}) y no en sus entities ni sus tablas: así las órdenes no
 * duplican la regla de "qué es el árbol" y los módulos se acoplan por su
 * contrato (design.md §8, punto 2).
 */
@Component
class MachineDirectoryAdapter implements MachineDirectory {

    private final MachineRepository machines;
    private final PartRepository parts;

    MachineDirectoryAdapter(MachineRepository machines, PartRepository parts) {
        this.machines = machines;
        this.parts = parts;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findMachineName(long machineId) {
        return machines.findById(machineId).map(Machine::name);
    }

    /**
     * Dos lecturas: la parte y todas las de su máquina, y se sube por
     * {@code parentId} hasta la raíz. El tope de iteraciones protege de un ciclo
     * que solo podría existir si alguien tocó la base a mano.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<PartLocation> locatePart(long partId) {
        Optional<Part> found = parts.findById(partId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Part part = found.get();
        Map<Long, Part> byId = new HashMap<>();
        for (Part sibling : parts.findByMachineId(part.machineId())) {
            byId.put(sibling.id(), sibling);
        }

        Deque<String> names = new ArrayDeque<>();
        Part current = part;
        int remaining = byId.size() + 1;
        while (current != null) {
            if (remaining-- == 0) {
                throw new IllegalStateException("Ciclo en el árbol de partes de la máquina " + part.machineId());
            }
            names.addFirst(current.name());
            current = current.parentId() == null ? null : byId.get(current.parentId());
        }
        return Optional.of(new PartLocation(part.machineId(), List.copyOf(names)));
    }
}
