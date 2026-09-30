package com.enterpriselab.api.shared.web;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.enterpriselab.api.auth.domain.UnknownSessionUserException;
import com.enterpriselab.api.shared.domain.ConflictException;
import com.enterpriselab.api.shared.domain.InvalidReferenceException;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.domain.ValidationFailedException;

/** Controller que solo existe para {@link RestExceptionHandlerTest}: dispara cada excepción a mano. */
@RestController
class TestExceptionsController {

    @PostMapping("/test/validate")
    void validate(@RequestBody @Valid TestRequest request) {
    }

    @GetMapping("/test/boom")
    void boom() {
        throw new IllegalStateException("boom");
    }

    @GetMapping("/test/validation-failed")
    void validationFailed() {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("legajo", "Debe tener entre 1 y 8 dígitos");
        details.put("firstName", "Es obligatorio");
        throw new ValidationFailedException(details);
    }

    @GetMapping("/test/invalid-reference")
    void invalidReference() {
        throw new InvalidReferenceException("UNKNOWN_TECHNICIAN", "No existe el técnico con legajo 9999");
    }

    @GetMapping("/test/unknown-session-user")
    void unknownSessionUser() {
        throw new UnknownSessionUserException("El usuario del token ya no existe: ghost");
    }

    @GetMapping("/test/not-found")
    void notFound() {
        throw new NotFoundException("No existe el técnico con legajo 9999");
    }

    @GetMapping("/test/conflict")
    void conflict() {
        throw new ConflictException("DUPLICATE_LEGAJO", "Ya existe un técnico con legajo 1001");
    }

    record TestRequest(@NotBlank String name) {
    }
}
