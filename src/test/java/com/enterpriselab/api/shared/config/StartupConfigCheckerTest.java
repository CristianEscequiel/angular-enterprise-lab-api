package com.enterpriselab.api.shared.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 06, tarea 2: REQ-6.1, REQ-7.1, 7.2, 7.3, 7.5 y NFR-1, sobre un entorno armado a mano. */
class StartupConfigCheckerTest {

    private static final String GOOD_SECRET = "x".repeat(32);

    private static MockEnvironment env(String secret, String password) {
        MockEnvironment env = new MockEnvironment();
        if (secret != null) {
            env.setProperty("app.jwt.secret", secret);
        }
        if (password != null) {
            env.setProperty("spring.datasource.password", password);
        }
        return env;
    }

    private static List<String> messages(Throwable throwable) {
        List<String> messages = new ArrayList<>();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            messages.add(String.valueOf(current.getMessage()));
        }
        return messages;
    }

    @Test
    void req7_1_aSecretOf16BytesFailsNamingTheVariableAndTheLength() {
        assertThatThrownBy(() -> StartupConfigChecker.check(env("a".repeat(16), "pwd")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("32")
                .hasMessageContaining("16");
    }

    @Test
    void req7_2_aSecretOfExactly32BytesPasses() {
        assertThatCode(() -> StartupConfigChecker.check(env(GOOD_SECRET, "pwd"))).doesNotThrowAnyException();
    }

    @Test
    void req7_2_theLengthIsMeasuredInUtf8BytesNotCharacters() {
        // 16 caracteres de 2 bytes = 32 bytes; 15 caracteres de 2 bytes = 30.
        assertThatCode(() -> StartupConfigChecker.check(env("é".repeat(16), "pwd"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> StartupConfigChecker.check(env("é".repeat(15), "pwd")))
                .hasMessageContaining("30");
    }

    @Test
    void req7_3_theMessageNeverContainsTheSecretValue() {
        String secret = "corto-secreto-123";
        assertThatThrownBy(() -> StartupConfigChecker.check(env(secret, "pwd")))
                .satisfies(e -> assertThat(messages(e)).noneMatch(m -> m.contains(secret)));
    }

    @Test
    void req7_5_aMissingOrBlankSecretFailsSayingItIsNotDefined() {
        for (String secret : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> StartupConfigChecker.check(env(secret, "pwd")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET no está definida");
        }
    }

    @Test
    void req6_1_aMissingOrBlankDbPasswordFailsNamingTheVariable() {
        for (String password : new String[] {null, "", "  "}) {
            assertThatThrownBy(() -> StartupConfigChecker.check(env(GOOD_SECRET, password)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DB_PASSWORD");
        }
    }

    @Test
    void nfr1_aFailureNeverIncludesTheDbPasswordValue() {
        String password = "super-clave-de-base-777";
        assertThatThrownBy(() -> StartupConfigChecker.check(env("corto", password)))
                .satisfies(e -> assertThat(messages(e)).noneMatch(m -> m.contains(password)));
    }

    @Test
    void theCheckerRunsAfterTheConfigFilesAreLoaded() {
        assertThat(new StartupConfigChecker().getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    void aPropertyFromAnyPropertySourceCounts() {
        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addLast(new MapPropertySource("extra",
                Map.of("app.jwt.secret", GOOD_SECRET, "spring.datasource.password", "pwd")));
        assertThatCode(() -> StartupConfigChecker.check(env)).doesNotThrowAnyException();
    }
}
