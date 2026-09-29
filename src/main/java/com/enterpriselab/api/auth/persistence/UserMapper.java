package com.enterpriselab.api.auth.persistence;

import com.enterpriselab.api.auth.domain.Role;
import com.enterpriselab.api.auth.domain.User;

/** Mapper manual entity → dominio (design.md §1: sin MapStruct en esta spec). */
final class UserMapper {

    private UserMapper() {
    }

    static User toDomain(UserEntity entity) {
        String legajo = entity.getTechnician() != null ? entity.getTechnician().getLegajo() : null;

        return new User(
                entity.getId(),
                entity.getUsername(),
                entity.getPasswordHash(),
                Role.fromValue(entity.getRole()),
                legajo);
    }
}
