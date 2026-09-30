package com.enterpriselab.api.auth.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.enterpriselab.api.maintenance.persistence.TechnicianEntity;

/**
 * Mapea 1:1 la tabla {@code users} de V1__init.sql. {@code role} se guarda
 * como el mismo string kebab-case que {@link com.enterpriselab.api.auth.domain.Role}
 * — el mapeo a enum de dominio lo hace {@link UserMapper}, no esta clase.
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 40)
    private String role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_id")
    private TechnicianEntity technician;

    protected UserEntity() {
        // JPA
    }

    public UserEntity(String username, String passwordHash, String role, TechnicianEntity technician) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.technician = technician;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public TechnicianEntity getTechnician() {
        return technician;
    }
}
