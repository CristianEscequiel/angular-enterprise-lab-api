package com.enterpriselab.api;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-5, REQ-6: con el perfil "dev" activo (única condición bajo la que
 * {@code V1_1__seed_users.sql} entra en {@code spring.flyway.locations},
 * ver application.yml), el seed deja los cuatro roles representados y cada
 * {@code password_hash} hasheado con BCrypt, nunca en texto plano.
 */
@ActiveProfiles("dev")
class SeedUsersIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedLoadsAllFourRoles() {
        List<String> roles = jdbcTemplate.queryForList("select distinct role from users", String.class);

        assertThat(roles).containsExactlyInAnyOrder(
                "administrador", "team-leader-mantenimiento", "personal-produccion", "tecnico");
    }

    @Test
    void seedPasswordsAreBcryptHashed() {
        List<String> hashes = jdbcTemplate.queryForList("select password_hash from users", String.class);

        assertThat(hashes).isNotEmpty();
        assertThat(hashes).allSatisfy(hash -> assertThat(hash).matches("^\\$2[aby]\\$.*"));
    }

    @Test
    void seededTecnicosHaveALegajo() {
        List<Map<String, Object>> tecnicos = jdbcTemplate.queryForList(
                "select username, technician_id from users where role = 'tecnico'");

        assertThat(tecnicos).hasSize(2);
        assertThat(tecnicos).allSatisfy(row -> assertThat(row.get("technician_id")).isNotNull());
    }

    /** REQ-16 (enmienda 00-A): nombre visible y correo de los cinco usuarios, iguales a {@code db.json} del frontend. */
    @Test
    void seedLoadsTheDisplayNameAndEmailOfEveryUser() {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "select username, display_name, email from users order by username");

        assertThat(users).extracting(row -> row.get("username") + "|" + row.get("display_name") + "|" + row.get("email"))
                .containsExactly(
                        "admin|Administrador|admin@enterprise-lab.dev",
                        "electricista|Técnico Electricista Preventivo|electricista@enterprise-lab.dev",
                        "produccion|Personal de Producción|produccion@enterprise-lab.dev",
                        "teamleader|Team Leader de Mantenimiento|teamleader@enterprise-lab.dev",
                        "tecnico|Técnico Mecánico de Guardia|tecnico@enterprise-lab.dev");
    }
}
