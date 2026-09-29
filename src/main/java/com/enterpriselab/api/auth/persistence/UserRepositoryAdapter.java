package com.enterpriselab.api.auth.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.enterpriselab.api.auth.domain.User;
import com.enterpriselab.api.auth.domain.UserRepository;

/**
 * Adaptador JPA del puerto {@link UserRepository} (patrón puerto-adaptador,
 * structure.md).
 *
 * <p>{@code @Transactional} acá, no {@code EAGER} en {@code technician}: el
 * mapeo a dominio ({@link UserMapper}) lee la asociación LAZY con
 * {@code technicians}, y sin una transacción abierta durante el mapeo eso
 * revienta con {@code LazyInitializationException} fuera de un request HTTP
 * (donde no hay {@code OpenEntityManagerInViewFilter} manteniendo la sesión).
 */
@Repository
class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    UserRepositoryAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(UserMapper::toDomain);
    }
}
