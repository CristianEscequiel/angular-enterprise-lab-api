package com.enterpriselab.api.auth.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * REQ-7: identidad del token autenticado. {@code legajo} solo aparece para
 * el rol {@code tecnico}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeResponse(String username, String role, String legajo) {
}
