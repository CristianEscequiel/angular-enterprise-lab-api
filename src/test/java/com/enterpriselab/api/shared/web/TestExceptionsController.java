package com.enterpriselab.api.shared.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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

    record TestRequest(@NotBlank String name) {
    }
}
