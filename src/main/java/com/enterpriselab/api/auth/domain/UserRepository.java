package com.enterpriselab.api.auth.domain;

import java.util.Optional;

/**
 * Puerto de dominio (patrón puerto-adaptador, ver structure.md). La
 * implementación JPA vive en {@code auth/persistence} (tarea 11); esta
 * interfaz no depende de Spring Data ni de JPA.
 */
public interface UserRepository {

    Optional<User> findByUsername(String username);
}
