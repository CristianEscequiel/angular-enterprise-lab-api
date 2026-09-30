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

    /** REQ-15, REQ-16: todos los usuarios traen el nombre visible y el correo del seed. */
    @Test
    void everySeededUserComesWithItsDisplayNameAndEmail() {
        assertThat(userRepository.findByUsername("admin").orElseThrow())
                .satisfies(user -> {
                    assertThat(user.displayName()).isEqualTo("Administrador");
                    assertThat(user.email()).isEqualTo("admin@enterprise-lab.dev");
                });
        assertThat(userRepository.findByUsername("tecnico").orElseThrow())
                .satisfies(user -> {
                    assertThat(user.displayName()).isEqualTo("Técnico Mecánico de Guardia");
                    assertThat(user.email()).isEqualTo("tecnico@enterprise-lab.dev");
                });
    }

    /** REQ-18: el perfil del técnico sale del maestro de técnicos (spec 01). */
    @Test
    void aTecnicoComesWithTheProfileOfItsTechnician() {
        User mecanico = userRepository.findByUsername("tecnico").orElseThrow();
        User electricista = userRepository.findByUsername("electricista").orElseThrow();

        assertThat(mecanico.legajo()).isEqualTo("1001");
        assertThat(mecanico.specialty()).isEqualTo("mecanico");
        assertThat(mecanico.teamType()).isEqualTo("guardia");
        assertThat(electricista.legajo()).isEqualTo("1002");
        assertThat(electricista.specialty()).isEqualTo("electricista");
        assertThat(electricista.teamType()).isEqualTo("preventivo-correctivo");
    }

    /** REQ-19: el resto de los roles no tiene perfil de técnico. */
    @Test
    void theOtherRolesComeWithoutTechnicianProfile() {
        for (String username : new String[] {"admin", "teamleader", "produccion"}) {
            User user = userRepository.findByUsername(username).orElseThrow();

            assertThat(user.legajo()).as(username).isNull();
            assertThat(user.specialty()).as(username).isNull();
            assertThat(user.teamType()).as(username).isNull();
        }
    }

    @Test
    void returnsEmptyForAnUnknownUsername() {
        assertThat(userRepository.findByUsername("no-existe")).isEmpty();
    }
}
