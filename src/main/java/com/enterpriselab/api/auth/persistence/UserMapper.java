package com.enterpriselab.api.auth.persistence;

import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.auth.domain.User;
import com.enterpriselab.api.maintenance.persistence.TechnicianEntity;

/**
 * Mapper manual entity → dominio (design.md §1: sin MapStruct en esta spec).
 * El perfil del técnico ({@code legajo}, {@code specialty}, {@code teamType})
 * se lee de {@link TechnicianEntity}, la fuente de verdad (REQ-18, REQ-21); un
 * usuario sin técnico asociado no tiene ninguno de los tres.
 */
final class UserMapper {

    private UserMapper() {
    }

    static User toDomain(UserEntity entity) {
        TechnicianEntity technician = entity.getTechnician();

        return new User(
                entity.getId(),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getDisplayName(),
                entity.getEmail(),
                Role.fromValue(entity.getRole()),
                technician != null ? technician.getLegajo() : null,
                technician != null ? technician.getSpecialty() : null,
                technician != null ? technician.getTeamType() : null);
    }
}
