package com.enterpriselab.api.workorders.persistence;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.enterpriselab.api.machines.domain.MachineRepository;
import com.enterpriselab.api.machines.domain.Part;
import com.enterpriselab.api.machines.domain.PartRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * El tope de iteraciones protege de un ciclo en el árbol. La base lo hace imposible por la
 * FK compuesta salvo que alguien la toque a mano (una parte que se apunta a sí misma
 * pasa la FK), así que se prueba con los puertos simulados.
 */
@ExtendWith(MockitoExtension.class)
class MachineDirectoryAdapterTest {

    @Mock
    private MachineRepository machines;
    @Mock
    private PartRepository parts;

    @Test
    void aSelfReferencingPartIsAnErrorAndNotAnInfiniteLoop() {
        Part loop = new Part(1L, 7L, 1L, "Bucle");
        when(parts.findById(1L)).thenReturn(Optional.of(loop));
        when(parts.findByMachineId(7L)).thenReturn(List.of(loop));

        assertThatThrownBy(() -> new MachineDirectoryAdapter(machines, parts).locatePart(1L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Ciclo");
    }

    @Test
    void aTwoPartCycleIsAnErrorToo() {
        Part a = new Part(1L, 7L, 2L, "A");
        Part b = new Part(2L, 7L, 1L, "B");
        when(parts.findById(1L)).thenReturn(Optional.of(a));
        when(parts.findByMachineId(7L)).thenReturn(List.of(a, b));

        assertThatThrownBy(() -> new MachineDirectoryAdapter(machines, parts).locatePart(1L))
                .isInstanceOf(IllegalStateException.class);
    }
}
