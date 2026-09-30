package com.enterpriselab.api.maintenance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mapea la tabla {@code team_members}: una fila por técnico de un equipo, con
 * su posición ({@code sort_order}) para conservar el orden de alta. Guarda los
 * ids como columnas planas, sin asociaciones JPA: el adaptador resuelve legajos
 * a ids y arma las filas él mismo.
 */
@Entity
@Table(name = "team_members")
class TeamMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "technician_id", nullable = false)
    private Long technicianId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected TeamMemberEntity() {
        // JPA
    }

    TeamMemberEntity(Long teamId, Long technicianId, int sortOrder) {
        this.teamId = teamId;
        this.technicianId = technicianId;
        this.sortOrder = sortOrder;
    }
}
