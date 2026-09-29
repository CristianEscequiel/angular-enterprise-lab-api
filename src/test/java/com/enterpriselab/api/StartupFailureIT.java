package com.enterpriselab.api;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-3: si la aplicación no puede conectarse a PostgreSQL al arrancar,
 * falla el arranque (fail fast) con un error que identifica el problema de
 * conexión, en vez de quedar en un estado parcialmente inicializado.
 *
 * <p>No extiende {@link AbstractPostgresIT} a propósito: acá se necesita un
 * puerto que nadie escucha, no el Postgres real de las demás IT.
 */
class StartupFailureIT {

    @Test
    void failsFastWhenDatabaseIsUnreachable() throws IOException {
        int closedPort = findClosedPort();

        SpringApplication app = new SpringApplication(ApiApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);

        assertThatThrownBy(() -> app.run(
                "--spring.datasource.url=jdbc:postgresql://localhost:" + closedPort + "/enterpriselab",
                "--spring.datasource.username=enterpriselab",
                "--spring.datasource.password=enterpriselab"))
                .satisfies(exception -> assertThat(causeMessages(exception))
                        .as("alguna causa de la cadena debe identificar el problema de conexión")
                        .anySatisfy(message -> assertThat(message).containsIgnoringCase("connection")));
    }

    /** Puerto que se abre y se cierra al toque: garantiza "connection refused" sin esperar timeouts de red. */
    private static int findClosedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static List<String> causeMessages(Throwable throwable) {
        List<String> messages = new ArrayList<>();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.add(current.getMessage());
            }
        }
        return messages;
    }
}
