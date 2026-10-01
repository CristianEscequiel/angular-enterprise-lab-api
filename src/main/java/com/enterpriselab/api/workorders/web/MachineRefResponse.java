package com.enterpriselab.api.workorders.web;

import com.enterpriselab.api.workorders.domain.MachineRef;

/** Referencia a la máquina y parte de una orden; {@code partId} es {@code null} si la orden es sobre la máquina completa. */
public record MachineRefResponse(String machineId, String partId, String breadcrumb, String comment) {

    static MachineRefResponse from(MachineRef ref) {
        return new MachineRefResponse(String.valueOf(ref.machineId()),
                ref.partId() == null ? null : String.valueOf(ref.partId()), ref.breadcrumb(), ref.comment());
    }
}
