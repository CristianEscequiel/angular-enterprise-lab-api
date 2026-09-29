# Tareas: Spec 00 — Proyecto base y seguridad

- [x] 1. Bootstrap del proyecto Spring Boot
  - Detalle: `build.gradle.kts` (Java 21 toolchain, Spring Boot 3.x) con las
    dependencias de `design.md` §1: web, data-jpa, validation, flyway,
    postgresql driver, actuator, `oauth2-resource-server`, springdoc,
    testcontainers (postgresql + junit-jupiter), JUnit 5, Mockito. Estructura
    de paquetes de `structure.md` (`shared/`, `auth/{web,domain,persistence,
    security}`) creada vacía. `ApiApplication` mínima.
  - Depende de: —
  - Verificación: `./gradlew build` compila sin errores.
  - _Requisitos: base técnica, sin REQ directo_

- [x] 2. `docker-compose.yaml` con PostgreSQL
  - Detalle: servicio `postgres:16-alpine`, puerto 5432 expuesto, mismas
    credenciales que usará `application.yml`, volumen nombrado, healthcheck
    con `pg_isready` (design.md §6).
  - Depende de: —
  - Verificación manual: `docker compose up -d` seguido de `docker compose ps`
    muestra el servicio en estado `healthy`.
  - _Requisitos: REQ-2_

- [x] 3. `application.yml` base, perfil `dev` y config de fail-fast
  - Detalle: bloque `spring.datasource`, `jpa.hibernate.ddl-auto: validate`,
    `flyway.locations`, `app.jwt.*`, `app.cors.*`,
    `server.error.include-stacktrace: never`; bloque de perfil `dev` con
    `flyway.locations` extendido a `db/seed` y secreto JWT por defecto solo
    para ese perfil (design.md §6).
  - Depende de: 1
  - Verificación: arranque con `docker compose up -d` (tarea 2) seguido de
    `./gradlew bootRun --args='--spring.profiles.active=dev'` no falla por
    config faltante (todavía no hay tablas, así que Flyway puede fallar acá;
    se resuelve en la tarea 7 — esta verificación es solo sobre el
    parseo/carga de la config).
  - _Requisitos: base para REQ-1, REQ-3, REQ-12_

- [x] 4. Base común de tests de integración (`AbstractPostgresIT`)
  - Detalle: clase base con `@Testcontainers` + `@ServiceConnection`
    levantando un único contenedor Postgres compartido para todas las IT
    (design.md §7).
  - Depende de: 1
  - Verificación: una IT trivial que extiende la base y levanta el contexto
    de Spring sin errores.
  - _Requisitos: infraestructura de verificación, sin REQ directo_

- [x] 5. Health check (`GET /actuator/health`)
  - Detalle: config mínima de Actuator (`show-details: never`), expuesto sin
    autenticación.
  - Depende de: 3, 4
  - Verificación: `HealthIT` — `GET /actuator/health` devuelve `200` con
    `{"status":"UP"}`.
  - _Requisitos: REQ-1_

- [x] 6. Fail fast si no hay conexión a la base de datos
  - Detalle: sin cambios de código más allá de la config por defecto de
    Hikari/Flyway (`initializationFailTimeout=1`, sin reintentos silenciosos);
    esta tarea es la que confirma el comportamiento.
  - Depende de: 3
  - Verificación: `StartupFailureIT` — arrancar `SpringApplication` apuntando
    a un puerto Postgres cerrado lanza una excepción cuya causa contiene
    `Connection`; el contexto no queda parcialmente inicializado.
  - _Requisitos: REQ-3_

- [x] 7. Migración Flyway `V1__init.sql` (tablas `technicians` y `users`)
  - Detalle: SQL de design.md §3 — `technicians(id, legajo UNIQUE NOT NULL)`,
    `users(id, username UNIQUE, password_hash, role CHECK IN (...),
    technician_id FK nullable)` con el `CHECK` cruzado
    `users_tecnico_has_legajo`.
  - Depende de: 4
  - Verificación: `MigrationIT` — las tablas existen con las columnas
    esperadas; insertar un usuario `tecnico` sin `technician_id` viola el
    `CHECK`; insertar un rol fuera de los cuatro valores viola el `CHECK`.
  - _Requisitos: REQ-4, REQ-5_

- [x] 8. Bean `PasswordEncoder` (BCrypt)
  - Detalle: `BCryptPasswordEncoder` (strength 10) expuesto como bean en
    `auth/security`.
  - Depende de: 1
  - Verificación: test unitario — `encode` seguido de `matches` sobre el
    mismo valor da `true`; el hash resultante empieza con `$2a$` o `$2b$`.
  - _Requisitos: REQ-6_

- [x] 9. Seed de usuarios de desarrollo (`db/seed/V1_1__seed_users.sql`)
  - Detalle: un usuario por cada uno de los cuatro roles, replicando los de
    `db.json` del frontend; hashes BCrypt precalculados con el encoder de la
    tarea 8; contraseñas en claro documentadas en el README (tarea 21).
    Solo se carga en el perfil `dev` (ya configurado en la tarea 3).
  - Depende de: 7, 8
  - Verificación: `MigrationIT` extendido — con el perfil `dev` activo, la
    tabla `users` tiene los cuatro roles representados y cada
    `password_hash` empieza con `$2a$`/`$2b$`.
  - _Requisitos: REQ-5, REQ-6_

- [x] 10. Dominio de autenticación (`auth/domain`)
  - Detalle: `User` (record), enum `Role` con `fromValue`/`toValue`
    kebab-case, puerto `UserRepository` (`findByUsername`), excepción
    `InvalidCredentialsException`. Sin anotaciones de Spring Data ni JPA
    (regla de `structure.md`).
  - Depende de: 7
  - Verificación: test unitario de `Role.fromValue` — los cuatro valores
    válidos resuelven al enum correcto y un valor inválido lanza excepción.
  - _Requisitos: REQ-5_

- [x] 11. Persistencia de usuarios (`auth/persistence`)
  - Detalle: `UserEntity`, `TechnicianEntity`, `UserJpaRepository` (Spring
    Data), `UserRepositoryAdapter` (implementa el puerto de dominio),
    `UserMapper` manual entity↔dominio.
  - Depende de: 10
  - Verificación: `MigrationIT` extendido (o IT propia) — `findByUsername`
    contra un usuario del seed devuelve un `User` de dominio con `role` y
    `legajo` correctos; `findByUsername` con un username inexistente
    devuelve vacío.
  - _Requisitos: REQ-4, REQ-5_

- [x] 12. Manejo global de errores (`shared/web`)
  - Detalle: `ApiError` (record: `code`, `message`, `timestamp`, `path`),
    `RestExceptionHandler` (`@RestControllerAdvice`) cubriendo validación
    (400), `NoResourceFoundException`/no encontrado (404) y excepción no
    controlada (500, sin stacktrace); `server.error.include-stacktrace: never`
    ya está en la tarea 3.
  - Depende de: 1
  - Verificación: test de slice (`@WebMvcTest` con un controller de prueba
    que lanza cada tipo de excepción) — cada caso devuelve el `code` y el
    HTTP status esperados con la forma `{code,message,timestamp,path}`.
  - _Requisitos: REQ-13_

- [x] 13. Emisión de JWT (`auth/security`: `JwtConfig`, `JwtTokenIssuer`)
  - Detalle: `JwtEncoder`/`JwtDecoder` (Nimbus, HS256) con clave desde
    `app.jwt.secret`; `JwtTokenIssuer.issue(User)` arma el token con claims
    `sub`, `role`, `legajo` (si aplica), `iat`, `exp` según `app.jwt.ttl`.
  - Depende de: 3
  - Verificación: test unitario — el token emitido, decodificado con el
    mismo `JwtDecoder`, expone los claims esperados; un usuario sin rol
    `tecnico` no lleva claim `legajo`.
  - _Requisitos: REQ-7_

- [x] 14. `AuthService.login`
  - Detalle: busca el usuario por el puerto `UserRepository`; si no existe,
    igual corre `passwordEncoder.matches` contra un hash dummy para no
    filtrar tiempos; en cualquier fallo lanza `InvalidCredentialsException`
    con el mismo mensaje.
  - Depende de: 8, 11, 13
  - Verificación: `AuthServiceTest` (Mockito) — credenciales válidas
    devuelven un token; usuario inexistente y contraseña incorrecta lanzan
    la misma excepción con el mismo mensaje.
  - _Requisitos: REQ-7, REQ-8_

- [x] 15. `POST /auth/login` (`AuthController`)
  - Detalle: `LoginRequest`/`LoginResponse` (DTOs, Bean Validation en
    `LoginRequest`), delega en `AuthService`, mapea
    `InvalidCredentialsException` → 401 vía el advice de la tarea 12.
    Nota: se adelantó una `SecurityConfig` mínima (rutas públicas
    `/actuator/health` y `POST /auth/login`, sin sesión ni CSRF), porque la
    cadena por defecto de Boot bloquea el login; la tarea 16 la completa.
  - Depende de: 12, 14
  - Verificación: `AuthControllerIT` — login con credenciales válidas
    devuelve `200` con token; usuario inexistente y contraseña incorrecta
    devuelven el **mismo** cuerpo `401`.
  - _Requisitos: REQ-7, REQ-8_

- [ ] 16. `SecurityConfig` + `JsonAuthenticationEntryPoint`
  - Detalle: filter chain de resource-server, rutas públicas
    (`/actuator/health`, `/auth/login`, `/swagger-ui.html`,
    `/v3/api-docs/**`) vs. autenticadas; `JsonAuthenticationEntryPoint`
    escribe el mismo formato `ApiError` que el advice (401 genérico, sin
    exponer si el token está expirado, mal firmado o malformado).
  - Depende de: 12, 13
  - Verificación: `SecurityConfigIT` — request a una ruta protegida sin
    header `Authorization` devuelve `401` con el `ApiError` esperado.
  - _Requisitos: REQ-9_

- [ ] 17. `GET /auth/me`
  - Detalle: endpoint protegido que lee el `Jwt` autenticado y devuelve
    `username`, `role`, `legajo`.
  - Depende de: 15, 16
  - Verificación: `AuthControllerIT` extendido — token válido devuelve `200`
    con los datos correctos; token expirado (TTL negativo en el test), mal
    firmado, o string `abc` devuelven `401` con el mismo `message` (sin
    distinguir el motivo).
  - _Requisitos: REQ-7, REQ-9, REQ-10_

- [ ] 18. Autorización por rol (`AccessPolicy`, `JsonAccessDeniedHandler`)
  - Detalle: `AccessPolicy.requireRole(Role...)` en `auth/domain`, lanza
    `ForbiddenOperationException` si el rol actual no está permitido (el
    advice de la tarea 12 la traduce a 403); `JsonAccessDeniedHandler`
    cubre el 403 que lanza el propio filtro de Spring Security antes de
    llegar al controller. Un controller de test, solo en `src/test`,
    restringido a `administrador` vía `AccessPolicy`, sirve para verificar
    el comportamiento sin publicar un endpoint artificial en producción.
  - Depende de: 16
  - Verificación: `AccessPolicyTest` (unitario) — rol no permitido lanza la
    excepción; rol permitido no lanza nada. `RoleRestrictionIT` — contra el
    controller de test, un token con rol `tecnico` recibe `403` y uno con
    `administrador` recibe `200`.
  - _Requisitos: REQ-11_

- [ ] 19. CORS para el frontend (`CorsProperties`, `CorsConfigurationSource`)
  - Detalle: orígenes desde `app.cors.allowed-origins` (`design.md` §6),
    métodos GET/POST/PUT/PATCH/DELETE, headers `Authorization` y
    `Content-Type`; si la lista de orígenes contiene `*`, la aplicación
    falla al arrancar (validación en `@ConfigurationProperties`).
  - Depende de: 3, 16
  - Verificación: `CorsIT` — preflight `OPTIONS` desde `http://localhost:4200`
    devuelve `Access-Control-Allow-Origin`; el mismo preflight desde
    `http://evil.com` no lo devuelve.
  - _Requisitos: REQ-12_

- [ ] 20. Documentación OpenAPI (springdoc)
  - Detalle: `springdoc-openapi-starter-webmvc-ui`, esquema de seguridad
    `bearerAuth` aplicado a los endpoints protegidos, metadata básica
    (título, versión) de la API.
  - Depende de: 15, 17
  - Verificación: `OpenApiIT` — `GET /v3/api-docs` responde `200` y el JSON
    incluye las rutas `/auth/login` y `/auth/me`; `/swagger-ui.html` responde
    `200`.
  - _Requisitos: REQ-14_

- [ ] 21. README de arranque
  - Detalle: pasos para levantar el proyecto (`docker compose up -d`,
    `./gradlew bootRun --args='--spring.profiles.active=dev'`), tabla de
    usuarios de seed con sus contraseñas en claro (referenciadas desde la
    tarea 9), y cómo correr los tests (`./gradlew test`).
  - Depende de: 2, 9
  - Verificación manual: seguir los pasos del README de punta a punta en un
    entorno limpio (clonar, `docker compose up -d`, `bootRun`, login con un
    usuario del seed) funciona sin pasos no documentados.
  - _Requisitos: soporte de REQ-2, REQ-5 — documentación, sin criterio EARS
    propio_

- [ ] 22. CI: GitHub Actions (build + test por PR)
  - Detalle: workflow que corre `./gradlew build` (incluye las IT con
    Testcontainers) en cada PR, mencionado como tarea propia en `tech.md`.
  - Depende de: 1–20 (corre toda la suite)
  - Opcional: sí
  - Verificación manual: abrir un PR de prueba y confirmar que el workflow
    se dispara y termina en verde.
  - _Requisitos: fuera del alcance de `requirements.md`, tomado de `tech.md`_

---
**Estado:** Aprobado — 2026-09-29
