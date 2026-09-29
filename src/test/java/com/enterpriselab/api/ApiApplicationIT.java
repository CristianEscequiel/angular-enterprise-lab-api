package com.enterpriselab.api;

import org.junit.jupiter.api.Test;

/**
 * IT trivial que solo prueba la base común (tarea 4 de tasks.md): que el
 * contexto de Spring levanta contra el Postgres de {@link AbstractPostgresIT}
 * sin errores. Los tests que verifican comportamiento real (REQ-1 en
 * adelante) llegan en las tareas siguientes.
 */
class ApiApplicationIT extends AbstractPostgresIT {

    @Test
    void contextLoads() {
        // Si el contexto no levanta, @SpringBootTest falla el test antes de
        // llegar acá.
    }
}
