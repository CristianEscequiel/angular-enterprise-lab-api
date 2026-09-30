package com.enterpriselab.api.shared.web;

import org.springframework.security.oauth2.jwt.Jwt;

import com.enterpriselab.api.auth.domain.Role;

/**
 * Resuelve el {@link Role} del claim {@code role} del token ya validado. Los
 * controllers lo pasan a los servicios de {@code domain}, que son los que
 * deciden si ese rol puede hacer la operación (AccessPolicy).
 */
public final class AuthenticatedRole {

    private AuthenticatedRole() {
    }

    public static Role from(Jwt jwt) {
        return Role.fromValue(jwt.getClaimAsString("role"));
    }
}
