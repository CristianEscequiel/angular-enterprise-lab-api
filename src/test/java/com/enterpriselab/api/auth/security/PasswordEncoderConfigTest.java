package com.enterpriselab.api.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderConfigTest {

    private final PasswordEncoder passwordEncoder = new PasswordEncoderConfig().passwordEncoder();

    @Test
    void encodesAndMatchesTheSameRawPassword() {
        String raw = "clave-de-prueba";

        String hash = passwordEncoder.encode(raw);

        assertThat(hash).matches("^\\$2[aby]\\$.*");
        assertThat(passwordEncoder.matches(raw, hash)).isTrue();
    }

    @Test
    void doesNotMatchADifferentRawPassword() {
        String hash = passwordEncoder.encode("clave-correcta");

        assertThat(passwordEncoder.matches("clave-incorrecta", hash)).isFalse();
    }
}
