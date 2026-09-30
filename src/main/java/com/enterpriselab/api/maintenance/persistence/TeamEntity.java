package com.enterpriselab.api.maintenance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mapea la tabla {@code teams}. Sin colección de miembros a propósito: se
 * manejan aparte con {@link TeamMemberEntity} para controlar el orden de los
 * {@code DELETE} e {@code INSERT} al reemplazarlos (design.md §1).
 */
@Entity
@Table(name = "teams")
class TeamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 30)
    private String type;

    protected TeamEntity() {
        // JPA
    }

    TeamEntity(String name, String type) {
        this.name = name;
        this.type = type;
    }

    void update(String name, String type) {
        this.name = name;
        this.type = type;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getType() {
        return type;
    }
}
