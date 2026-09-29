package com.enterpriselab.api.auth.persistence;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;
import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.auth.domain.User;
import com.enterpriselab.api.auth.domain.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-4, REQ-5: {@code findByUsername} contra el seed de la tarea 9 (perfil
 * "dev") devuelve un {@link User} de dominio con {@code role} y
 * {@code legajo} correctos, y vacío si el username no existe.
 */
@ActiveProfiles("dev")
class UserRepositoryAdapterIT extends AbstractPostgresIT {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findsASeededTecnicoWithRoleAndLegajo() {
        Optional<User> found = userRepository.findByUsername("tecnico");

        assertThat(found).isPresent();
        User user = found.orElseThrow();
        assertThat(user.role()).isEqualTo(Role.TECNICO);
        assertThat(user.legajo()).isEqualTo("1001");
    }

    @Test
    void findsASeededStaffUserWithoutLegajo() {
        Optional<User> found = userRepository.findByUsername("admin");

        assertThat(found).isPresent();
        User user = found.orElseThrow();
        assertThat(user.role()).isEqualTo(Role.ADMINISTRADOR);
        assertThat(user.legajo()).isNull();
    }

    @Test
    void returnsEmptyForAnUnknownUsername() {
        assertThat(userRepository.findByUsername("no-existe")).isEmpty();
    }
}
