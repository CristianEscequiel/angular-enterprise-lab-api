package com.enterpriselab.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base para los tests de integración de la Spec 00 (design.md §7).
 *
 * <p>Usa el patrón de "singleton container" de Testcontainers: el contenedor
 * se arranca una única vez en el {@code static} initializer y no se detiene
 * explícitamente (lo limpia Ryuk al terminar la JVM), de forma que todas las
 * subclases de este archivo, en toda la suite, reutilizan el mismo Postgres
 * en vez de levantar uno por clase.
 *
 * <p>No lleva {@code @Testcontainers}/{@code @Container} a propósito: esas
 * anotaciones atan el ciclo de vida del contenedor a cada clase de test
 * (arranca en {@code @BeforeAll}, para en {@code @AfterAll}), que es
 * exactamente lo que este patrón evita. {@code @ServiceConnection} no
 * depende de esas anotaciones — Spring Boot detecta el campo estático
 * anotado y configura el {@code DataSource} de test contra el contenedor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractPostgresIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
