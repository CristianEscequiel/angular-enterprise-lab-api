package com.enterpriselab.api.workorders.domain;

import java.util.Optional;

/**
 * Lo que las órdenes necesitan saber del maestro de máquinas y partes para armar
 * el {@code breadcrumb}. Puerto de {@code domain}: el adaptador vive en
 * {@code persistence} y se apoya en los puertos públicos del módulo
 * {@code machines}.
 */
public interface MachineDirectory {

    Optional<String> findMachineName(long machineId);

    Optional<PartLocation> locatePart(long partId);
}
