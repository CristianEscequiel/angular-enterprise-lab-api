package com.enterpriselab.api.auth.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** REQ-5 (legajo) y REQ-15, REQ-18, REQ-19 (perfil completo) del lado del dominio. */
class UserTest {

    private static User tecnico(String displayName, String email, String legajo, String specialty, String teamType) {
        return new User(1L, "tecnico", "hash", displayName, email, Role.TECNICO, legajo, specialty, teamType);
    }

    private static User staff(Role role, String legajo, String specialty, String teamType) {
        return new User(1L, "staff", "hash", "Nombre", "staff@test.dev", role, legajo, specialty, teamType);
    }

    // --- REQ-5 -------------------------------------------------------------------------------

    @Test
    void aTecnicoWithItsFullProfileIsValid() {
        assertThatCode(() -> tecnico("Técnico", "t@test.dev", "1001", "mecanico", "guardia"))
                .doesNotThrowAnyException();
    }

    @Test
    void aNonTecnicoWithoutProfileIsValid() {
        assertThatCode(() -> staff(Role.ADMINISTRADOR, null, null, null)).doesNotThrowAnyException();
    }

    @Test
    void aTecnicoWithoutLegajoIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> tecnico("Técnico", "t@test.dev", null, "mecanico", "guardia"));
    }

    @Test
    void aNonTecnicoWithLegajoIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> staff(Role.ADMINISTRADOR, "1001", null, null));
    }

    // --- REQ-15: nombre visible y correo -----------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void aMissingOrBlankDisplayNameIsRejected(String displayName) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new User(1L, "admin", "hash", displayName, "a@test.dev", Role.ADMINISTRADOR, null, null, null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void aMissingOrBlankEmailIsRejected(String email) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new User(1L, "admin", "hash", "Administrador", email, Role.ADMINISTRADOR, null, null, null));
    }

    // --- REQ-18: el técnico tiene su perfil --------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aTecnicoWithoutSpecialtyIsRejected(String specialty) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> tecnico("Técnico", "t@test.dev", "1001", specialty, "guardia"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aTecnicoWithoutTeamTypeIsRejected(String teamType) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> tecnico("Técnico", "t@test.dev", "1001", "mecanico", teamType));
    }

    // --- REQ-19: el resto de los roles no lo tiene -------------------------------------------

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMINISTRADOR", "TEAM_LEADER_MANTENIMIENTO", "PERSONAL_PRODUCCION"})
    void aNonTecnicoWithSpecialtyOrTeamTypeIsRejected(Role role) {
        assertThatIllegalArgumentException().isThrownBy(() -> staff(role, null, "mecanico", null));
        assertThatIllegalArgumentException().isThrownBy(() -> staff(role, null, null, "guardia"));
        assertThatIllegalArgumentException().isThrownBy(() -> staff(role, null, "mecanico", "guardia"));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMINISTRADOR", "TEAM_LEADER_MANTENIMIENTO", "PERSONAL_PRODUCCION"})
    void everyNonTecnicoRoleIsValidWithoutProfile(Role role) {
        assertThatCode(() -> staff(role, null, null, null)).doesNotThrowAnyException();
    }
}
