package com.enterpriselab.api.maintenance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mapea la tabla {@code technicians} completa (V1 + V2 + V3). Es la única
 * entity de esa tabla: {@code UserEntity} (auth) la referencia por la FK
 * {@code users.technician_id}, así que es pública. {@code specialty} y
 * {@code teamType} se guardan como el mismo string kebab-case que los enums de
 * dominio; el mapeo lo hace {@link TechnicianMapper}, no esta clase.
 */
@Entity
@Table(name = "technicians")
public class TechnicianEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String legajo;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 20)
    private String specialty;

    @Column(name = "team_type", nullable = false, length = 30)
    private String teamType;

    protected TechnicianEntity() {
        // JPA
    }

    public TechnicianEntity(String legajo, String firstName, String lastName, String specialty, String teamType) {
        this.legajo = legajo;
        this.firstName = firstName;
        this.lastName = lastName;
        this.specialty = specialty;
        this.teamType = teamType;
    }

    /** La edición de un técnico cambia estos cuatro campos; el legajo no se toca (REQ-12). */
    void updateProfile(String firstName, String lastName, String specialty, String teamType) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.specialty = specialty;
        this.teamType = teamType;
    }

    public Long getId() {
        return id;
    }

    public String getLegajo() {
        return legajo;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getSpecialty() {
        return specialty;
    }

    public String getTeamType() {
        return teamType;
    }
}
