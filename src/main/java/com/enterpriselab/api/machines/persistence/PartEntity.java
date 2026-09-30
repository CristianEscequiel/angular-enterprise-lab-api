package com.enterpriselab.api.machines.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mapea la tabla {@code parts} con los ids sueltos, sin {@code @ManyToOne}: no
 * hay carga perezosa ni N+1, y {@code machineId} y {@code parentId} no tienen
 * setter porque una parte no se mueve (REQ-25).
 */
@Entity
@Table(name = "parts")
class PartEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "machine_id", nullable = false, updatable = false)
    private Long machineId;

    @Column(name = "parent_id", updatable = false)
    private Long parentId;

    @Column(nullable = false, length = 100)
    private String name;

    protected PartEntity() {
        // JPA
    }

    PartEntity(Long machineId, Long parentId, String name) {
        this.machineId = machineId;
        this.parentId = parentId;
        this.name = name;
    }

    void rename(String name) {
        this.name = name;
    }

    Long getId() {
        return id;
    }

    Long getMachineId() {
        return machineId;
    }

    Long getParentId() {
        return parentId;
    }

    String getName() {
        return name;
    }
}
