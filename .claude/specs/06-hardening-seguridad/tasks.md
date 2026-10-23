# Tareas: Spec 06 — Hardening de seguridad

Entorno: compilar requiere JDK 21 (`JAVA_HOME`) y Docker para las IT. Toda tarea deja
`./gradlew test` en verde. Esta spec **no tiene migraciones ni dependencias nuevas** (NFR-2).
Los tests llevan el id del criterio en el nombre (por ejemplo `req1_1_…`, `req1_1b_…`) para que
el verificador de cierre asocie cada subcriterio. Rutas: `J/` = `src/main/java/com/enterpriselab/api/`,
`T/` = `src/test/java/com/enterpriselab/api/`, `R/` = `src/main/resources/`,
`TR/` = `src/test/resources/`.

Orden: 1 → 2 → 6; 3 → 4 → 5a → 5b; 4 → 7; 8 aparte; todo → 9 → 10. Las tareas 3 y 8 no
dependen de ninguna otra. Se hacen en serie las que comparten archivos: `application.yml`
(2 y 6) y `RestExceptionHandler`, su test y `TestExceptionsController` (4 y 7).

Los `@SpringBootTest` que cambian propiedades usan `@TestPropertySource(properties = …)` sobre
una subclase de `AbstractPostgresIT`, para no redeclarar `@SpringBootTest` (que perdería
`spring.flyway.locations`).

- [x] 1. Base de pruebas: propiedades de test
  - Detalle: crear `TR/config/application.properties` con `app.jwt.secret` (≥ 32 bytes),
    `spring.datasource.password` y los límites del login holgados
    (`app.security.login-rate-limit.max-attempts-per-user=1000`, `…per-ip=1000`; la
    propiedad todavía no existe y se ignora). Se usan estos nombres y no `JWT_SECRET` /
    `DB_PASSWORD` para no confundirlos con las variables reales (design.md §5). No cambia
    código de producción.
  - Depende de: —
  - Verificación: `./gradlew test` completo sigue en verde con la misma cantidad de tests que
    antes de empezar (1123). Es la red de seguridad para las tareas 2 y 6.
  - _Requisitos: NFR-3_

- [x] 2. Validación de `JWT_SECRET` y `DB_PASSWORD` al arrancar
  - Detalle: `J/shared/config/StartupConfigChecker` (`EnvironmentPostProcessor`,
    `Ordered.LOWEST_PRECEDENCE`) registrado en `R/META-INF/spring.factories`. Lee
    `app.jwt.secret` (vacío → "JWT_SECRET no está definida"; < 32 bytes UTF-8 → mensaje con
    el mínimo y la longitud, sin el valor) y `spring.datasource.password` (vacío →
    "DB_PASSWORD no está definida"); lanza `IllegalStateException`. `R/application.yml`:
    `secret: ${JWT_SECRET:}`, `password: ${DB_PASSWORD:}` en la base y
    `password: ${DB_PASSWORD:enterpriselab}` en el bloque `dev`; se agrega
    `server.forward-headers-strategy: none`.
  - Depende de: 1
  - Verificación:
    - `StartupConfigCheckerTest` (`ConfigurableEnvironment` + `MockPropertySource`):
      `req7_1_` 16 bytes falla; `req7_2_` 32 bytes pasa; `req7_3_` el mensaje no contiene el
      valor ni en la cadena de causas (NFR-1); `req7_5_` vacío falla nombrando `JWT_SECRET`;
      `req6_1_` `DB_PASSWORD` vacía falla nombrándola; `nfr1_` el fallo por `DB_PASSWORD` con un
      valor no vacío pero con `JWT_SECRET` inválido no incluye el valor de `DB_PASSWORD`.
    - `StartupConfigIT` (`SpringApplication` sin web, `--spring.config.location=classpath:/application.yml`,
      datasource a un puerto cerrado como `StartupFailureIT`): `req6_2_` y `req7_4_` con
      `--spring.profiles.active=dev` y sin variables ninguna causa nombra `JWT_SECRET` ni
      `DB_PASSWORD`; sin `dev`, `--JWT_SECRET=` vacío falla; con 32 bytes y sin `DB_PASSWORD`
      falla nombrándola; `req6_3_` con ambas, un `ApplicationEnvironmentPreparedEvent`
      captura `spring.datasource.password` con el valor dado.
    - `StartupFailureIT` existente sigue verde.
  - _Requisitos: REQ-6.1, 6.2, 6.3, REQ-7.1 a 7.5, NFR-1_

- [x] 3. Limitador de intentos de login (dominio y propiedades)
  - Detalle: `J/auth/domain/LoginAttemptLimiter` (sin anotaciones de Spring; `acquire`,
    `recordFailure`, `recordSuccess`, `release`, `size`, `purgeExpired`, `reset`; contadores
    `reserved` y `failures`; `synchronized`; ventana desde el primer fallo; design.md §3.1) y
    `J/auth/domain/TooManyAttemptsException` (`Duration retryAfter()`, mensaje fijo de
    REQ-3.1b). `J/auth/security/LoginRateLimitProperties` (record, `@DefaultValue` 5/20/15m,
    constructor compacto que nombra la propiedad si el valor es ≤ 0) y
    `LoginRateLimitConfig` (`@EnableConfigurationProperties`, bean del limiter con el `Clock`).
    Todavía no se usa desde `AuthService`.
  - Depende de: —
  - Verificación:
    - `LoginAttemptLimiterTest` (reloj mutable): `req1_1_` el 5.º intento fallido pasa;
      `req1_1b_` el 6.º lanza `TooManyAttemptsException`, aunque sea con éxito; `req1_2_`
      usuario inexistente se bloquea igual (mismo `retryAfter`); `req1_3_` 4 fallos + éxito + 4
      fallos no bloquea; `req1_4_` `Admin` y ` admin ` comparten contador; `req1_5_` pasados 15
      minutos desde el fallo que bloqueó vuelve a pasar y 1 s antes no; `req2_1_` el 21.º
      intento desde una IP lanza aunque el usuario sea otro; `req2_2_` otra IP no se afecta;
      `req2_4_` desbloqueo tras 15 minutos; `req3_5_` insistir bloqueado no cambia
      `retryAfter` ni extiende el bloqueo; `req4_3_` tras vencer todo, `purgeExpired()` deja
      `size() == 0`; `req4_4_` 50 hilos con umbral 5 → exactamente 5 pasan; además `release`
      y un éxito que deja `reserved = 0` eliminan la entrada, y un login exitoso aislado no
      abre ventana.
    - `LoginRateLimitPropertiesTest` (`ApplicationContextRunner` con `LoginRateLimitConfig` y
      un `Clock`): `req4_1_` con `max-attempts-per-user=2` el 3.er `acquire` lanza (el umbral
      observado cambia con la propiedad), y lo mismo con `max-attempts-per-ip` y `window`;
      `req4_1b_` sin propiedades el umbral es 5/20/15m; `req4_2_` `0` y `abc` fallan con el
      nombre de la propiedad en el mensaje.
  - _Requisitos: REQ-1.1 a 1.5, REQ-2.1, 2.2, 2.4, REQ-3.5, REQ-4.1 a 4.4_

- [x] 4. Respuesta 429 y cabecera expuesta por CORS
  - Detalle: en `RestExceptionHandler`, handler de `TooManyAttemptsException` → `429`
    `TOO_MANY_ATTEMPTS` con `Retry-After = max(1, ceil(segundos))` y el `message` fijo. En
    `CorsConfig`, `setExposedHeaders(List.of("Retry-After"))`. En
    `T/shared/web/TestExceptionsController`, endpoint `GET /test/too-many?seconds=` que lanza
    `TooManyAttemptsException` con el `retryAfter` pedido (el controlador de pruebas está en el
    paquete escaneado, así que también lo ven los IT).
  - Depende de: 3
  - Verificación:
    - `RestExceptionHandlerTest`: `req3_1_` cuerpo `{code, message, timestamp, path}`;
      `req3_1b_` el mismo `message` para cualquier `retryAfter`; `req3_2_` `Retry-After` entero
      ≥ 1, redondeado hacia arriba (200 ms → `1`).
    - `CorsIT`: `req3_3_` `GET /test/too-many` con token y `Origin` permitido responde `429` y
      `Access-Control-Expose-Headers` contiene `Retry-After`.
  - _Requisitos: REQ-3.1, 3.1b, 3.2, 3.3_

- [x] 5a. Integrar el limitador en `AuthService` y `AuthController`
  - Detalle: `AuthService.login(username, password, clientIp)` con el flujo de design.md §3.3
    (`acquire` antes de consultar el repositorio o BCrypt; `recordFailure` / `recordSuccess`;
    `release` ante excepción inesperada). `AuthController` pasa `request.getRemoteAddr()`.
    Actualizar `AuthServiceTest`: los 2 constructores (líneas 37 y 108) y las 4 llamadas a
    `login` (líneas 47, 60, 111 y 119) para pasar la IP.
  - Depende de: 3, 4
  - Verificación: `AuthServiceTest` — `req3_4_` con el limitador bloqueado,
    `verifyNoInteractions` del repositorio de usuarios y del `PasswordEncoder`; un fallo
    llama `recordFailure`; un éxito llama `recordSuccess`; una excepción del repositorio
    llama `release`. `AuthControllerIT` y las IT que inician sesión siguen verdes.
  - _Requisitos: REQ-1, REQ-2, REQ-3.4_

- [x] 5b. Pruebas de integración del límite y documentación OpenAPI del 429
  - Detalle: `@ApiResponse(responseCode = "429", headers = @Header(name = "Retry-After"))` en
    `AuthController.login`. Tests nuevos `LoginRateLimitIT` (MockMvc; límites 5/20/15m con
    `@TestPropertySource`; `Clock` mutable como `@TestConfiguration`) y
    `LoginRateLimitRealHttpIT` (`RANDOM_PORT`, cliente real; `max-attempts-per-ip=5` y
    `max-attempts-per-user=1000` por `@TestPropertySource`).
  - Depende de: 5a
  - Verificación:
    - `LoginRateLimitIT`: `req1_1_` el 5.º fallo es `401`; `req1_1b_` el 6.º es `429` con la
      contraseña correcta; `req1_4b_` 10 cuerpos vacíos o sin `username` dan `400` y luego un
      login válido da `200`; `req2_1_` y `req2_2_` con `remoteAddr` distinto; `req3_1_`
      cuerpo de 429; `req3_2_` `Retry-After` igual al restante ±1 s; `req3_6_`
      `GET /auth/me` con una IP bloqueada sigue respondiendo; `nfr1_` ni el cuerpo del 429
      ni el log (`ListAppender`) contienen la contraseña enviada.
    - `LoginRateLimitRealHttpIT`: `req2_3_` `X-Forwarded-For` distinto en cada request no evita
      el bloqueo, y el `Environment` real tiene `server.forward-headers-strategy=none`.
    - `OpenApiIT`: `req14_3_` `/v3/api-docs` incluye `429` con cabecera `Retry-After` en
      `POST /auth/login`.
  - _Requisitos: REQ-1, REQ-2, REQ-3.1 a 3.6, REQ-14.3, NFR-1_

- [x] 6. Swagger y OpenAPI solo con `dev`
  - Detalle: `R/application.yml` base con `springdoc.api-docs.enabled: false` y
    `springdoc.swagger-ui.enabled: false`; el bloque `dev` los pone en `true`.
    `SecurityConfig` agrega los `permitAll` de Swagger y `/v3/api-docs/**` solo si las
    propiedades están en `true` (design.md §3.7). `/actuator/health` queda igual.
  - Depende de: 1, 2 (comparten `application.yml`)
  - Verificación:
    - `OpenApiDisabledIT` (sin `dev`): `req5_1_` los tres recursos dan `401` sin token;
      `req5_1b_` `404` con `ApiError` con un token válido (`AbstractPostgresIT` carga el seed
      aun sin `dev`: se inicia sesión como `admin`); `req5_3_` no existe el bean
      `OpenApiWebMvcResource`.
    - `OpenApiIT` existente (`dev`): `req5_2_` `/v3/api-docs` y `/swagger-ui.html` dan `200`
      sin token.
    - `SecurityConfigIT` sigue verde.
  - _Requisitos: REQ-5.1, 5.1b, 5.2, 5.3_

- [x] 7. Excepciones estándar de Spring MVC con `ApiError`
  - Detalle: en `RestExceptionHandler`, handlers de `HttpRequestMethodNotSupportedException`
    (`405` `METHOD_NOT_ALLOWED` + `Allow`), `HttpMediaTypeNotSupportedException` (`415`
    `UNSUPPORTED_MEDIA_TYPE`), `MissingServletRequestParameterException` y
    `MethodArgumentTypeMismatchException` (`400` `VALIDATION_ERROR` con `details`).
    `Exception.class` y `NoResourceFoundException` no cambian. En
    `T/shared/web/TestExceptionsController`, endpoints de prueba con `@RequestParam` obligatorio
    y tipado (`int`).
  - Depende de: 1, 4 (comparten `RestExceptionHandler` y el controlador de pruebas)
  - Verificación:
    - `RestExceptionHandlerTest` (MockMvc standalone): `req10_1_` parámetro faltante → `400`
      con `details.<param>`; `req10_2_` tipo inválido → `400`; `req12_1_` la excepción
      inesperada sigue dando `500` con `INTERNAL_ERROR` y sin el nombre de la excepción en el
      cuerpo; verbo no admitido y `Content-Type` inválido contra los endpoints de prueba
      (formato y cabecera `Allow`).
    - `WebErrorsIT` (`dev`, token de `admin`): `req8_1_` `DELETE /work-orders` → `405` con
      `Allow`; `req9_1_` `POST /work-orders` con `text/plain` → `415`; `req9_2_`
      `POST /auth/login` con `text/plain` → `415`; `req10_3_` `GET /work-orders?page=abc` →
      `400` con `details.page`; `req11_1_` `GET /no-existe` autenticado → `404` `NOT_FOUND`;
      `req11_2_` sin token → `401`.
  - _Requisitos: REQ-8.1, REQ-9.1, 9.2, REQ-10.1 a 10.3, REQ-11.1, 11.2, REQ-12.1_

- [x] 8. `409` cuando se agotan los reintentos de una orden
  - Detalle: en `WorkOrderFlowService.attempt`, al agotar `MAX_ATTEMPTS`: declarar un `Logger`
    SLF4J (`WorkOrderFlowService` no tiene), `log.warn` con el id y
    `throw new ConflictException("WORK_ORDER_CONCURRENT_UPDATE", …, Map.of())`, en lugar de
    `IllegalStateException`. Sin cambios en `take`/`close`/`release`/`cancel`.
  - Depende de: —
  - Verificación: `WorkOrderFlowServiceTest` — `req13_1_` repositorio que devuelve
    `Optional.empty()` 3 veces en `take`, `close`, `release` y `cancel` ⇒ `ConflictException`
    con el código y nunca `IllegalStateException`, con `findById` llamado 3 veces;
    `req13_2_` no hay otras llamadas de escritura al repositorio; `req13_3_` un
    `ListAppender` sobre el logger de `WorkOrderFlowService` captura el `warn` con el id de la
    orden. `WorkOrderFlowConcurrencyIT` existente sigue verde.
  - _Requisitos: REQ-13.1, 13.2, 13.3_

- [x] 9. Documentación y `notes.md`
  - Detalle: `README.md`: sección "Variables de producción" (`JWT_SECRET` ≥ 32 bytes,
    `DB_PASSWORD` obligatoria fuera de `dev`, `APP_CORS_ALLOWED_ORIGINS` lista separada por
    comas y sin comodín); sección del límite de login (en memoria, una instancia, se
    reinicia, `app.security.login-rate-limit.max-attempts-per-user` / `…per-ip` / `…window`
    con 5, 20 y 15m, `429` y `Retry-After`); tabla de endpoints con Swagger/OpenAPI "solo con
    el perfil `dev`" y los códigos nuevos (`405`, `415`, `429`, `409 WORK_ORDER_CONCURRENT_UPDATE`);
    corregir la frase "sin él la aplicación no arranca" y el `DB_PASSWORD` "opcional"; nota
    sobre `SPRINGDOC_SWAGGER_UI_ENABLED` (design.md §7.6). Crear `notes.md` en la carpeta de
    la spec y pegar ahí la salida de las verificaciones.
  - Depende de: 2, 5b, 6, 7, 8
  - Verificación (manual, salida pegada en `notes.md`): `grep -n "JWT_SECRET"` (`req14_1_`),
    `grep -n "DB_PASSWORD"` (`req14_1b_`), `grep -n "APP_CORS_ALLOWED_ORIGINS"` y
    `grep -n -i "comodín"` (`req14_1c_`), `grep -n "max-attempts-per-user"`,
    `grep -n "max-attempts-per-ip"`, `grep -n "15m"` y `grep -n -i "en memoria"` (`req14_2_`),
    `grep -n -i "perfil .dev"` sobre la tabla de endpoints y ninguna línea que diga
    Swagger "público" (`req14_4_`).
  - _Requisitos: REQ-14.1, 14.2, 14.4_

- [x] 10. Cierre de la spec
  - Detalle: `./gradlew build` completo; revisar el diff (NFR-2: `db/migration` y
    `build.gradle.kts` sin cambios); actualizar `.claude/specs/ROADMAP.md` (spec 06 hecha;
    "operación" restante pasa a la 11), `CLAUDE.md` (estado) y `ROADMAP-post-auditoria.md`
    (C3 lado API hecho); completar `notes.md` con el orden de despliegue: **API primero**,
    **confirmar antes `JWT_SECRET` y `DB_PASSWORD` en producción (F0)**, invalidar tokens
    emitidos con la clave literal, y la enmienda 06-A pendiente por el proxy. Invocar a
    `spec-verifier` y completar la "Verificación final" de `requirements.md`.
  - Depende de: 1 a 9
  - Verificación: `./gradlew build` en verde (`NFR-3`); `git diff --stat` sin
    `db/migration` ni `build.gradle.kts` (`NFR-2`); el `spec-verifier` devuelve `SÍ` en todos
    los criterios o los `NO VERIFICABLE` quedan confirmados contigo.
  - _Requisitos: NFR-2, NFR-3; todos al verificar_

## Trazabilidad

| Requisito | Tarea |
|---|---|
| REQ-1.1 a 1.5 | 3, 5b |
| REQ-2.1 a 2.4 | 3, 5b |
| REQ-3.1, 3.1b, 3.2, 3.3 | 4, 5b |
| REQ-3.4 | 5a |
| REQ-3.5 | 3 |
| REQ-3.6 | 5b |
| REQ-4.1 a 4.4 | 3 |
| REQ-5.1 a 5.3 | 6 |
| REQ-6.1 a 6.3, REQ-7.1 a 7.5 | 2 |
| REQ-8, REQ-9, REQ-10, REQ-11, REQ-12 | 7 |
| REQ-13 | 8 |
| REQ-14.1, 14.2, 14.4 | 9 |
| REQ-14.3 | 5b |
| NFR-1 | 2, 5b |
| NFR-2, NFR-3 | 1, 10 |

---
**Estado:** Aprobado el 2026-10-22
