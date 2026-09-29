package com.enterpriselab.api.auth.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data — detalle de infraestructura. Paquete-privado a
 * propósito: fuera de {@code persistence} solo se conoce el puerto de
 * dominio {@link com.enterpriselab.api.auth.domain.UserRepository}, que
 * implementa {@link UserRepositoryAdapter}.
 */
interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);
}
