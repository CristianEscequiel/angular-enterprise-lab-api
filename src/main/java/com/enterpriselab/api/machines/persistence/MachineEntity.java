package com.enterpriselab.api.machines.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Mapea la tabla {@code machines}. Sin colección de partes: el árbol se lee como lista plana. */
@Entity
@Table(name = "machines")
class MachineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    protected MachineEntity() {
        // JPA
    }

    MachineEntity(String code, String name) {
        this.code = code;
        this.name = name;
    }

    void update(String code, String name) {
        this.code = code;
        this.name = name;
    }

    Long getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }
}
