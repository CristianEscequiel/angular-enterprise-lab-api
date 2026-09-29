package com.enterpriselab.api.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mínima a propósito (solo legajo) — la spec de mantenimiento la extiende
 * con especialidad y tipo de equipo (design.md §2).
 */
@Entity
@Table(name = "technicians")
public class TechnicianEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String legajo;

    protected TechnicianEntity() {
        // JPA
    }

    public TechnicianEntity(String legajo) {
        this.legajo = legajo;
    }

    public Long getId() {
        return id;
    }

    public String getLegajo() {
        return legajo;
    }
}
