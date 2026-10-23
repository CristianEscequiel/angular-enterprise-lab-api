package com.enterpriselab.api;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Spec 06, tarea 2: REQ-6.2, 6.3 y REQ-7.4 contra el {@code application.yml} real. Salta el
 * archivo de pruebas ({@code config/application.properties}) con
 * {@code --spring.config.location=classpath:/application.yml}. No necesita Postgres: el arranque
 * apunta a un puerto cerrado y lo que se afirma es si el fallo viene del chequeo de configuración
 * (antes de cualquier bean) o de la conexión (después).
 */
class StartupConfigIT {

    private static final String GOOD_SECRET = "s".repeat(40);

    private static Throwable run(AtomicReference<ConfigurableEnvironment> captured, String... args)
            throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        SpringApplication app = new SpringApplication(ApiApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.addListeners(event -> {
            if (event instanceof ApplicationEnvironmentPreparedEvent prepared) {
                captured.set(prepared.getEnvironment());
            }
        });
        List<String> all = new ArrayList<>(List.of(
                "--spring.config.location=classpath:/application.yml",
                "--spring.datasource.url=jdbc:postgresql://localhost:" + closedPort + "/enterpriselab"));
        all.addAll(List.of(args));
        return catchThrowable(() -> app.run(all.toArray(new String[0])));
    }

    private static List<String> messages(Throwable throwable) {
        List<String> messages = new ArrayList<>();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            messages.add(String.valueOf(current.getMessage()));
        }
        return messages;
    }

    @Test
    void req6_2_and_req7_4_theDevProfileStartsPastTheCheckWithoutVariables() throws IOException {
        Throwable failure = run(new AtomicReference<>(), "--spring.profiles.active=dev");

        assertThat(failure).as("sin base de datos el arranque igual termina fallando").isNotNull();
        assertThat(messages(failure)).noneMatch(m -> m.contains("JWT_SECRET") || m.contains("DB_PASSWORD"));
    }

    @Test
    void req7_5_withoutDevAnEmptyJwtSecretFailsAndNamesIt() throws IOException {
        Throwable failure = run(new AtomicReference<>(), "--JWT_SECRET=", "--DB_PASSWORD=pwd");

        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
    }

    @Test
    void req7_1_withoutDevAShortJwtSecretFails() throws IOException {
        Throwable failure = run(new AtomicReference<>(), "--JWT_SECRET=" + "a".repeat(16), "--DB_PASSWORD=pwd");

        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
    }

    @Test
    void req6_1_withoutDevAMissingDbPasswordFailsAndNamesIt() throws IOException {
        Throwable failure = run(new AtomicReference<>(), "--JWT_SECRET=" + GOOD_SECRET);

        assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void nfr1_theStartupFailureNeverPrintsTheSecretValue() throws IOException {
        String secret = "tiny-zzzzz";
        Throwable failure = run(new AtomicReference<>(), "--JWT_SECRET=" + secret, "--DB_PASSWORD=pwd");

        assertThat(messages(failure)).noneMatch(m -> m.contains(secret));
    }

    @Test
    void req6_3_theDefinedDbPasswordIsTheOneUsed() throws IOException {
        AtomicReference<ConfigurableEnvironment> captured = new AtomicReference<>();

        Throwable failure = run(captured, "--JWT_SECRET=" + GOOD_SECRET, "--DB_PASSWORD=clave-de-prueba");

        assertThat(captured.get().getProperty("spring.datasource.password")).isEqualTo("clave-de-prueba");
        assertThat(messages(failure)).noneMatch(m -> m.contains("DB_PASSWORD no está definida"));
    }
}
