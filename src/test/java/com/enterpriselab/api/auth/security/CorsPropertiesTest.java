package com.enterpriselab.api.auth.security;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** REQ-12: un comodín en los orígenes es un error de configuración. */
class CorsPropertiesTest {

    @Test
    void explicitOriginsAreAccepted() {
        assertThat(new CorsProperties(List.of("http://localhost:4200")).allowedOrigins())
                .containsExactly("http://localhost:4200");
    }

    @Test
    void wildcardIsRejected() {
        assertThatThrownBy(() -> new CorsProperties(List.of("http://localhost:4200", "*")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("*");
    }
}
