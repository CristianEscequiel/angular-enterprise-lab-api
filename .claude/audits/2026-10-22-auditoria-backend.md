# Auditoría del backend — angular-enterprise-lab-api

Fecha: 2026-10-22 · Rama: `feat/cancelar-orden` · Modo: solo lectura (se ejecutó `./gradlew test` y `./gradlew dependencies --offline`, que solo escriben en `build/`).

Rutas relativas a `angular-enterprise-lab-api/`. Prefijo `J/` = `src/main/java/com/enterpriselab/api/`, `T/` = `src/test/java/com/enterpriselab/api/`, `R/` = `src/main/resources/`.

Estados: CUMPLE / PARCIAL / NO CUMPLE / NO VERIFICABLE.

**Resumen:** 1123 tests, 0 fallos, 0 errores, 0 omitidos, 71 clases, `BUILD SUCCESSFUL in 2m 37s`. La base es sólida (BCrypt, JWT validado por Spring, `UPDATE` condicional contra concurrencia, formato de error uniforme). Los desvíos importantes están en las reglas de órdenes: no existe `startDate`/`endDate`, una orden cerrada se puede editar y borrar, el mínimo del comentario es 50 (no 200), y `release` no deja registro. Además no hay rate limiting en el login.

---

## A. Seguridad

| # | Punto | Estado | Evidencia | Observación |
|---|---|---|---|---|
| 1 | Hash de contraseñas | CUMPLE | `J/auth/security/PasswordEncoderConfig.java:12-20` (`BCryptPasswordEncoder`, costo 10); `R/db/seed/V1_1__seed_users.sql:15-23` | El seed guarda hashes `$2a$10$…`, no texto plano. Las claves en claro solo están en el README (usuarios dev). `AuthService.java:23-29` usa un hash dummy para igualar tiempos. |
| 2 | JWT | PARCIAL | `JwtConfig.java:29-39`; `JwtTokenIssuer.java:37-51`; `application.yml:31`; `AuthControllerIT.java:216,248` | HS256 con secreto simétrico. Claims: `sub`, `role`, `legajo` (solo técnico), `iat`, `exp`. TTL 60 min. Firma y `exp` los valida `NimbusJwtDecoder` en cada request (validadores por defecto de Spring; hay IT con token vencido, mal firmado y malformado). **No hay refresh token** (no existe endpoint ni código). No hay `iss`/`aud`/`jti`, por lo que no hay revocación. |
| 3 | CORS | CUMPLE | `J/auth/security/CorsConfig.java:23-31`; `CorsProperties.java:15-21`; `application.yml:32-34`; `T/auth/security/CorsIT.java` | Un único origen: `http://localhost:4200`. Un `*` hace fallar el arranque. Métodos y headers acotados. Sin `allowCredentials`. Ojo: el origen de producción no está definido en `application.yml`; hay que inyectarlo por propiedad/variable al desplegar. |
| 4 | Rate limiting / fuerza bruta en `POST /auth/login` | NO CUMPLE | `J/auth/web/AuthController.java:35-39`; `SecurityConfig.java:29-33` | Búsqueda de `rate`, `bucket4j`, `throttle`, `lockout` en `src/main`: sin resultados (los únicos `attempt` son los reintentos de `WorkOrderFlowService`). Sin límite por IP/usuario, ni bloqueo, ni delay progresivo. BCrypt coste 10 es la única fricción. |
| 5 | Autorización por endpoint vs README | PARCIAL | Ver tabla 5-bis | La regla real vive en `domain` (`*Permissions` + `AccessPolicy.requireRole`), no en `@PreAuthorize`/`requestMatchers`; en `SecurityConfig` solo hay `permitAll` + `anyRequest().authenticated()`. Una discrepancia de contenido (ver abajo). |
| 6 | Secretos | PARCIAL | `application.yml:7,30,49`; `docker-compose.yaml:8`; `.github/workflows/ci.yml:48,83-85` | `JWT_SECRET` no tiene default fuera de `dev`, **pero tampoco hace fallar el arranque**: Spring deja `${JWT_SECRET}` como texto literal al enlazar `@ConfigurationProperties` y la app arranca firmando con esa clave de 13 bytes (los IT sin perfil `dev` arrancan sin la variable). **Corrección del 2026-10-22**, detectada al diseñar la spec 06; la versión original de este informe decía que el arranque fallaba. `StartupFailureIT` solo prueba la conexión a la base. El default `dev-only-secret-change-me-32-bytes-min` solo está en el perfil `dev`. **Matiz:** `DB_PASSWORD` tiene default `enterpriselab` en el `application.yml` base (no solo en `dev`) y el usuario/URL de BD están hardcodeados; si en prod no se define `DB_PASSWORD`, usaría la clave por defecto. No se valida longitud mínima de `JWT_SECRET` en código (solo lo dice el comentario). |
| 7 | Actuator y Swagger fuera de `dev` | NO CUMPLE | `SecurityConfig.java:30,32`; `application.yml:18-24`; `build.gradle.kts:25,36` | Actuator: solo `health` queda expuesto (default de Boot, `show-details: never`) → aceptable. **Swagger UI y `/v3/api-docs/**` son públicos en todos los perfiles**: no hay `springdoc.api-docs.enabled=false` ni `springdoc.swagger-ui.enabled=false` fuera de `dev`. Expone el contrato completo de la API en producción. |

### 5-bis. Endpoints y regla real

| Endpoint | Regla real (código) | README | ¿Coincide? |
|---|---|---|---|
| `POST /auth/login`, `GET /actuator/health`, `/swagger-ui*`, `/v3/api-docs/**` | `permitAll` (`SecurityConfig.java:30-32`) | público | Sí |
| `GET /auth/me` | autenticado; 401 si el usuario ya no existe (`AuthController.java:46`) | autenticado | Sí |
| `GET/POST/PUT /technicians…` | admin, team leader (`MaintenancePermissions.java:17-18`) | admin, TL | Sí |
| `DELETE /technicians/{legajo}` | admin (`MaintenancePermissions.java:22-23`) | admin | Sí |
| `/teams…` (GET/POST/PUT/DELETE) | solo team leader (`MaintenancePermissions.java:27-28`) | TL | Sí |
| `GET /machines`, `/machines/{id}`, `/machines/{id}/parts` | cualquier rol (`MachinesPermissions.java:18-19`) | autenticado | Sí |
| `POST/PUT/DELETE /machines…`, `POST /machines/{id}/parts`, `PATCH/DELETE /parts/{id}` | admin, TL (`MachinesPermissions.java:23-24`) | admin, TL | Sí |
| `GET /work-orders`, `/work-orders/{id}` | cualquier rol (`WorkOrderPermissions.java:21-22`) | autenticado | Sí |
| `POST /work-orders` | TL (`preventivo`, `correctivo`); producción (`pronto-intervencion`) (`WorkOrderPermissions.java:26-45`) | igual | Sí |
| `PUT /work-orders/{id}` | admin, TL, **en cualquier estado** (`WorkOrderService.java:128-144`) | admin, TL, cualquier estado | Sí (README refleja el comportamiento, que contradice la decisión acordada: punto 9) |
| `DELETE /work-orders/{id}` | admin, **en cualquier estado** (`WorkOrderService.java:146-150`) | admin | Sí (ídem) |
| `POST /work-orders/{id}/take` | técnico + equipo que atiende el tipo (`WorkOrderFlowService.java:51-63`) | igual | Sí |
| `POST /work-orders/{id}/close` | técnico dueño, equipo habilitado (`WorkOrderFlowService.java:65-88`) | igual | Sí |
| `POST /work-orders/{id}/release` | admin, TL (`WorkOrderPermissions.java:66-67`) | igual | Sí |
| `POST /work-orders/{id}/cancel` | admin, TL, solo `pending` (`WorkOrderPermissions.java:71-72`) | igual | Sí |
| `GET /dashboard/summary` | cualquier rol (`DashboardPermissions.java:15-16`) | autenticado | Sí |
| `GET /dashboard/workload` | admin, TL (`DashboardPermissions.java:19-20`) | admin, TL | Sí |

**Discrepancias README ↔ código:** (a) el README dice que Swagger es "público" sin aclarar que lo es también en producción; (b) el README no menciona que `/close` con `outcome: cancelled` es otra vía de cancelación (punto 11). No hay otras diferencias de roles.

---

## B. Reglas de negocio de órdenes

| # | Punto | Estado | Evidencia | Observación |
|---|---|---|---|---|
| 8 | Comentario de cierre | NO CUMPLE | `J/workorders/domain/WorkOrderFlowService.java:36-37,175-186`; `WorkOrderPermissions` n/a; `R/db/migration/V7__work_orders_schema.sql:28` (`VARCHAR(500)`) | Límite real: **50 a 500** caracteres (tras `strip()`), validado en `validateComment` (dominio). La decisión acordada era mínimo 200. Coincide con el README (50-500), por lo tanto el README también está desalineado con la decisión. No hay `@Size` en el DTO `WorkOrderCloseRequest`; la validación está solo en dominio. |
| 9 | Solo lectura tras cierre | NO CUMPLE | `WorkOrderService.java:128-150`; `WorkOrderJpaRepository.java` (sin guarda de estado en `updateDetails`); `WorkOrderController.java:208-215` ("en cualquier estado") | `PUT` y `DELETE` **no consultan `status`**: una orden `completed` o `cancelled` se edita (título, descripción, prioridad) y se borra. `PUT` lo pueden hacer admin y TL; `DELETE` el admin. El README lo documenta como intencional ("en cualquier estado"), lo cual contradice la decisión acordada. Sin test que lo impida. |
| 10 | `startDate` / `endDate` | NO CUMPLE | `grep -ri "startDate\|start_date\|endDate\|end_date" src` → 0 resultados. Columnas equivalentes: `V7__work_orders_schema.sql:27` (`taken_at`), `:31` (`closed_at`); `DashboardStatisticsAdapter.java:38-39` | **No existen** en entidad, migraciones ni DTO con ese nombre. Hay equivalentes: `taken_at` (se completa al tomar, `WorkOrderFlowService.java:61`) y `closed_at` (al cerrar y al cancelar, `:85-86,110`). Se exponen anidados en `takenBy.at` y `closingNote.at`, no como `startDate`/`endDate`. **`release` borra `taken_at`** (`WorkOrderJpaRepository.java:33-34`), así que la fecha de inicio se pierde. **El tiempo promedio usa `created_at`**, no `startDate`/`taken_at`: `avg(extract(epoch from closed_at - created_at))/60` (`DashboardStatisticsAdapter.java:38`); el README también lo dice ("entre `createdAt` y el cierre"). Mide espera + ejecución, no resolución. |
| 11 | Cancelación | PARCIAL | `WorkOrderFlowService.java:65-88,102-112`; `WorkOrderPermissions.java:61,72`; `WorkOrderJpaRepository.java:26-30,38-39`; `V9__work_orders_cancel_pending.sql:13-21` | **Vía 1 — `POST /close` con `outcome: cancelled`**: solo técnico dueño de una orden `in-progress` y de equipo habilitado; exige comentario de 50-500; completa `closed_at` y conserva el dueño. **Vía 2 — `POST /cancel`**: admin y TL, solo desde `pending`; **sin comentario** (ignora el cuerpo); completa `closed_at` y `closing_author_*` pero sin dueño ni `closingNote` en la respuesta. Una cancelación administrativa queda sin motivo. Dos vías con reglas distintas para el mismo estado final. Un admin/TL no puede cancelar una orden `in-progress` (primero `release`, luego `cancel`). |
| 12 | `release` | PARCIAL | `WorkOrderFlowService.java:90-100`; `WorkOrderJpaRepository.java:32-35`; `WorkOrderRepositoryAdapter.java:130-134` | Comportamiento: admin/TL, solo `in-progress` → `pending`; pone `taken_by_*` y `taken_at` en NULL. **No deja registro de quién ni cuándo liberó**: no hay tabla de historial/auditoría ni columna; el `UPDATE` no recibe el usuario ni la hora. Tampoco se registra a quién se le quitó la orden. |
| 13 | Grafía del estado | CUMPLE | `V7__work_orders_schema.sql:36-37`; `J/workorders/domain/WorkOrderStatus.java`; `R/db/migration/V9…:19` | Se usa **`cancelled`** (doble `l`) de forma consistente en el `CHECK` de la BD, el enum, el JSON (`outcome`) y los tests. No aparece `canceled` en el código. |
| 14 | Exclusividad al tomar (concurrencia) | CUMPLE | `WorkOrderJpaRepository.java:19-23` (`UPDATE … WHERE id = :id AND status = 'pending'`); `WorkOrderFlowService.java:121-135` (reintenta hasta 3 veces); `T/workorders/web/WorkOrderFlowConcurrencyIT.java:72-230` | `UPDATE` condicional (sin `@Version` ni `FOR UPDATE`) + `CHECK`s de invariantes (V8/V9). Hay IT con dos hilos: un solo ganador, el perdedor recibe `409` con el dueño real; también close vs release, close tardío vs reasignación y cancel vs take. Observación: si tras 3 intentos sigue sin aplicar, lanza `IllegalStateException` → `500` (caso muy improbable). |
| 15 | Especialidad y tipo de equipo al tomar | PARCIAL | `WorkOrderPermissions.java:76-85`; `WorkOrderFlowService.java:55,141-143`; `UserMapper` (perfil) | La regla de equipo coincide: `guardia` → `pronto-intervencion`; `preventivo-correctivo` → `preventivo` y `correctivo`; sin `teamType` → 403. **Pero no se usa la especialidad**: no existe `canTechnicianHandle` en el backend ni se compara `specialty` (mecánico/electricista/general) con la orden. `general` no tiene efecto alguno. Además el `teamType` sale del atributo del técnico (`technicians.team_type`), no de la membresía real en `teams`/`team_members` (ver punto 18). Si la decisión acordada incluía la especialidad, no está implementada. |

---

## C. Dominio de técnicos, equipos y máquinas

| # | Punto | Estado | Evidencia | Observación |
|---|---|---|---|---|
| 16 | Crear usuarios con rol `tecnico` | CUMPLE | `grep` de `userRepository.save`/`INSERT INTO users` en `src/main` → solo el seed; `UserRepository` solo expone lectura (`J/auth/domain/UserRepository.java`); `V1__init.sql:20` (`CHECK ((role='tecnico') = (technician_id IS NOT NULL))`) | **No existe ningún endpoint ni servicio que cree usuarios**. Los usuarios solo vienen del seed (`dev`). Consecuencia: en producción no existe ningún usuario ni forma de crearlo vía API (ver Riesgos). El `CHECK` de la BD impide un `tecnico` sin legajo. |
| 17 | Agregar técnico a un equipo por legajo | CUMPLE | `J/maintenance/domain/TeamService.java:125-152`; `V2__maintenance_schema.sql:30` | Valida formato (1 a 8 dígitos), duplicados en la lista (`:132`) y existencia en el maestro (`UNKNOWN_TECHNICIAN`, 400). `UNIQUE (team_id, technician_id)` respalda en BD. Reemplazo completo de miembros en `PUT /teams/{id}`. |
| 18 | Un técnico en más de un equipo / coherencia con `teamType` | NO CUMPLE | `V2__maintenance_schema.sql:23-30`; `TeamService.java:53-69` | Un técnico **puede estar en varios equipos** (solo hay `UNIQUE(team_id, technician_id)`, no por técnico). **No se valida** que `team.type` coincida con `technician.teamType`: se puede meter a un técnico `guardia` en un equipo `preventivo-correctivo`. Hay dos fuentes de verdad (`technicians.team_type` y la membresía) y las autorizaciones usan la primera. |

---

## D. Contrato y errores

| # | Punto | Estado | Evidencia | Observación |
|---|---|---|---|---|
| 19 | Formato `{code, message, timestamp, path}` | PARCIAL | `J/shared/web/RestExceptionHandler.java:193-282`; `JsonAuthenticationEntryPoint.java:40-47`; `JsonAccessDeniedHandler.java:34-41`; `SecurityConfig.java:34-40` | 400, 401, 403, 404, 409 y 500 usan `ApiError`; el filtro de seguridad usa handlers propios (incluido el del resource-server), con JSON. **Fisura:** `@ExceptionHandler(Exception.class)` (`:277`) captura también excepciones estándar de Spring MVC que normalmente serían 4xx: `HttpRequestMethodNotSupportedException` (405), `HttpMediaTypeNotSupportedException` (415), `MissingServletRequestParameterException` y `MethodArgumentTypeMismatchException` (400) terminarían como **500 `INTERNAL_ERROR`**. Inferido por lectura del código: no hay test (búsqueda de 405/415 en `src/test` → sin resultados) ni lo ejecuté contra la app. |
| 20 | Filtrado de stack traces / mensajes internos | CUMPLE | `application.yml:14-16` (`include-stacktrace: never`); `RestExceptionHandler.java:277-282` | El 500 responde mensaje genérico "Ocurrió un error inesperado" y loguea el detalle solo en el servidor. Matiz: `WorkOrderRepositoryAdapter.java:65-70` documenta que un `CHECK` violado sale como 500 genérico (sin fuga). |
| 21 | OpenAPI | PARCIAL | `J/shared/web/OpenApiConfig.java`; `WorkOrderController.java:141-215`; `T/shared/web/OpenApiIT.java`; 228 anotaciones `Operation/ApiResponse/Schema/Tag` | Endpoints con `@Operation` y `@ApiResponses` de 200/400/404/409 y `ApiError`. **Los 401/403 no están anotados** por endpoint (se ve en `take/close/release/cancel`). El texto de la descripción del API es mínimo ("autenticación y autorización por rol"). Versión `0.1.0` fija. Documentación y comportamiento coinciden en lo revisado, salvo los estados 405/415 mencionados en el punto 19. |

---

## E. Tests

| # | Punto | Estado | Evidencia | Observación |
|---|---|---|---|---|
| 22 | Cobertura por área | PARCIAL | `T/**` (71 clases) | Ver "Escenarios sin test" abajo. |
| 23 | `./gradlew test` | CUMPLE | `build/test-results/test/*.xml`; log en el scratchpad | **1123 tests, 0 fallos, 0 errores, 0 omitidos, 71 clases; BUILD SUCCESSFUL en 2 min 37 s** (con `--no-daemon`, Docker Desktop activo, JDK Temurin 21.0.11). Coincide con el "1123" de `CLAUDE.md`. |
| 24 | Test de 403 por rol en cada endpoint protegido | PARCIAL | `T/auth/web/RoleRestrictionIT.java`; `MachineControllerIT`, `PartControllerIT`, `TeamControllerIT`, `TechnicianControllerIT`, `WorkOrderControllerIT`, `WorkOrderFlowControllerIT`, `DashboardControllerIT:266`; `*PermissionsTest` | Hay tests de permisos por módulo (unitarios sobre la matriz y IT por controller). No verifiqué una matriz exhaustiva endpoint × rol; la cobertura parece amplia pero está repartida. No hay un test único que recorra todos los endpoints y roles. |

### Escenarios sin test (o sin evidencia de test)
- **Rate limiting / fuerza bruta** en login: no existe la funcionalidad, tampoco test.
- **Refresh token**: no existe.
- **405 / 415 / parámetros faltantes** sobre `RestExceptionHandler`: sin test (riesgo de 500, punto 19).
- **Editar y borrar una orden cerrada** (`completed`/`cancelled`): sin test de rechazo (hoy el comportamiento permitido no se verifica ni se prohíbe).
- **`startDate`/`endDate`**: no existen; el promedio por `taken_at` no se prueba.
- **Auditoría de `release`** (quién/cuándo): no existe.
- **Especialidad** en `take`: sin test porque no hay lógica.
- **Técnico en varios equipos / tipo de equipo distinto del `teamType`**: sin test que lo impida.
- **Swagger deshabilitado fuera de `dev`**: sin test (`SecurityConfigIT` cubre el acceso público, no la desactivación).
- **Agotamiento de los 3 reintentos** de `attempt()` (`IllegalStateException` → 500): no encontré test.
- **Tests de la imagen Docker / `docker-compose`**: no hay.

---

## F. Infraestructura y despliegue

| # | Punto | Estado | Evidencia | Observación |
|---|---|---|---|---|
| 25 | Workflows | CUMPLE | `.github/workflows/ci.yml` (único) | Disparo: `pull_request` y `push` a `master`. Jobs: **`build`** (Temurin 21, `./gradlew build`, incluye tests con Testcontainers; sin job de lint/checkstyle/análisis estático ni escaneo de dependencias), **`docker`** (solo push; buildx + login GHCR + `metadata-action` + `build-push-action`, tags `sha` y `latest`, caché GHA), **`deploy`** (solo push y `vars.DEPLOY_ENABLED == 'true'`; SSH con clave al VPS, puerto 5068, usuario `root`, comando `api sha-xxxxxxx`, `known_hosts` fijado, `concurrency` sin cancelar). Secretos: `GITHUB_TOKEN`, `DEPLOY_HOST`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`. Las acciones están pinadas por tag mayor (`@v4`, `@v5`, `@v6`), no por SHA. No hay rollback en el workflow (si existe, está en el script del servidor: no verificable). Hay un job `deploy` hacia `root@` (el servidor restringe el comando, según el comentario; no verificable). |
| 26 | Dockerfile | PARCIAL | `Dockerfile:4,21-30` | Multi-stage **sí** (`eclipse-temurin:21-jdk` → `21-jre-alpine`). Imagen final con **JRE**. Usuario **no root** (`USER app`). `-XX:MaxRAMPercentage=60` definido (no hay `-Xmx`). Faltan: `HEALTHCHECK`, imágenes fijadas por digest, y `-XX:+ExitOnOutOfMemoryError` o similar. El `COPY` de `app.jar` queda con el dueño root (se ejecuta como `app` solo con permiso de lectura: funciona). |
| 27 | `docker-compose.yaml` | PARCIAL | `docker-compose.yaml:1-20` | **Solo desarrollo**: únicamente `postgres:16-alpine`; no define el servicio de la API. Tiene healthcheck (`pg_isready`) y volumen persistente `postgres_data`. Expone el puerto `5432:5432` al host y el password por defecto `enterpriselab`. No hay compose de producción en el repo (el despliegue usa un script del servidor, fuera del repo). |
| 28 | Perfiles de Spring | CUMPLE | `application.yml:36-49` | Existe un único perfil nombrado: **`dev`**. Cambia: (1) Flyway suma `classpath:db/seed`; (2) `app.jwt.secret` tiene default. No hay perfil `prod`/`test` en `src/main`; "prod" es la ausencia de `dev`. Los tests usan Testcontainers (`AbstractPostgresIT`). |
| 29 | Migraciones | CUMPLE | `R/db/migration/V1…V9`; `R/db/seed/V1_1, V2_1, V4_1, V6_1, V7_1`; `build.gradle.kts:31-32`; búsqueda de `INSERT/UPDATE/DELETE` en `db/migration` → 0 | Flyway 11.7.2. Las migraciones de esquema no contienen datos; el seed vive aparte (`db/seed`) y solo se carga en `dev`. Riesgo de diseño: las versiones de seed (`V1_1`, `V2_1`…) se intercalan en la misma numeración; si un entorno que arrancó sin `dev` luego lo activa, Flyway fallaría por orden (`outOfOrder` no está activo). |

---

## G. Calidad y proceso

| # | Punto | Estado | Evidencia | Observación |
|---|---|---|---|---|
| 30 | `.claude/` y `CLAUDE.md` | PARCIAL | `.claude/specs/00…05`, `.claude/steering/{product,structure,tech}.md`, `CLAUDE.md:45-145`; `~/.claude/agents/` | Todas las `tasks.md` están completas (0 casillas `[ ]`; 94 `[x]`). **Pendientes declarados** en `CLAUDE.md`: verificación manual del README en entorno limpio (tarea 21 de spec 00), CI en verde en un PR (todas las specs) y "cierre formal" de la spec 00 (REQ-1 a REQ-14). `ROADMAP.md` marca spec 06 (operación/producción) como opcional y sin hacer. **No hay `agents/`, `skills/` ni `commands/` dentro de `.claude/` del repo.** Los subagentes (`code-auditor`, `implementer`, `spec-reviewer`, `spec-verifier`, `test-writer`) existen a nivel usuario (`~/.claude/agents/`, junto a un `spec-verifier.md.bak`), por lo que no viajan con el repositorio. |
| 31 | Dependencias | PARCIAL | `build.gradle.kts:3,36`; `./gradlew dependencies --offline` | Spring Boot **3.5.16**; Spring Security 6.5.11; Tomcat 10.1.55; Hibernate 6.6.53; Flyway 11.7.2; PostgreSQL JDBC 42.7.11; Jackson 2.21.4; Logback 1.5.34; **springdoc 2.9.1**; **nimbus-jose-jwt 9.37.4** (viejo; la 9.x actual es posterior). Gradle 8.14.4. No se instaló ni ejecutó ningún escáner de CVE (OWASP/Dependabot), por lo que **no puedo afirmar si hay vulnerabilidades conocidas**; marcar como no verificable. Candidatos a revisar: `nimbus-jose-jwt 9.37.4` y `springdoc 2.9.1` por antigüedad. No hay Dependabot/Renovate configurado (`.github/` solo tiene `ci.yml`). |
| 32 | README vs código | PARCIAL | `README.md` completo | Refleja bien endpoints, roles, errores y seeds. Desfases: (a) el README documenta como válidos comportamientos que contradicen las decisiones acordadas (comentario 50-500, editar/borrar en cualquier estado, promedio con `createdAt`); (b) no menciona que Swagger es público también en producción; (c) no menciona la ausencia de rate limiting ni de refresh token; (d) no explica cómo obtener un primer usuario en producción; (e) `CLAUDE.md` sigue diciendo "Pendiente de la spec 00: su cierre formal" y el README dice "Java 21 + Spring Boot 3" sin la versión; (f) no documenta la vía `close` con `outcome: cancelled` como cancelación, salvo en el endpoint de `close`. |

---

## Desvíos respecto de las decisiones acordadas (puntos 8 a 15)

| # | Decisión acordada | Estado real | Gravedad |
|---|---|---|---|
| 8 | Comentario de cierre ≥ 200 caracteres | Mínimo **50**, máximo 500 (`WorkOrderFlowService.java:36-37`). README y specs repiten 50-500. | Media |
| 9 | Orden `completed`/`cancelled` no editable ni borrable por nadie | `PUT` y `DELETE` funcionan en cualquier estado (`WorkOrderService.java:128-150`). | **Alta** |
| 10 | `startDate`/`endDate` automáticas; promedio con `startDate` | No existen con ese nombre; `taken_at`/`closed_at` equivalentes; `release` borra `taken_at`; el promedio usa `created_at` (`DashboardStatisticsAdapter.java:38`). | Media |
| 11 | Cancelación con reglas definidas | Dos vías con reglas distintas; la administrativa (`/cancel`) no exige comentario. | Media |
| 12 | `release` con registro de quién y cuándo | No deja registro. | Media |
| 15 | `canTechnicianHandle` con especialidad (`general` cubre ambas) | Solo se compara `teamType` con el tipo de orden; la especialidad no interviene. | Media (según lo acordado) |

Los puntos 13 (grafía `cancelled`) y 14 (concurrencia) cumplen.

---

## Riesgos

### Alta
1. **Órdenes cerradas editables y borrables** (punto 9). Impacto: un admin/TL puede alterar el título, la descripción o la prioridad de una orden ya cerrada, y un admin puede borrarla, perdiendo el historial y la trazabilidad (incluidas las notas de cierre). Rompe la regla de solo lectura acordada.
2. **Sin protección contra fuerza bruta en `/auth/login`** (punto 4). Impacto: ataque de diccionario ilimitado contra usuarios conocidos (`admin`, `teamleader`); los usuarios y claves del seed están publicados en el README y existen si se arranca con `dev` por error.
3. **Swagger y `/v3/api-docs` públicos en producción** (punto 7). Impacto: se publica el mapa completo de la API, DTOs y códigos de error a cualquiera, lo que facilita el reconocimiento.
4. **No hay forma de crear usuarios fuera del seed** (punto 16). Impacto: en producción (sin `dev`) la base queda sin ningún usuario; el login es imposible o se debe insertar a mano en SQL, con la posibilidad de errores operativos y claves sin política.

### Media
5. **Excepciones estándar de Spring MVC devueltas como 500** (punto 19). Impacto: un método HTTP incorrecto, un `Content-Type` no soportado o un parámetro mal tipado responden 500 `INTERNAL_ERROR` en lugar de 405/415/400; ensucia logs (`log.error`) y alertas, y engaña a los clientes. *Inferido por lectura; sin test.*
6. **Sin historial de `release` y pérdida de `taken_at`** (puntos 10 y 12). Impacto: no hay trazabilidad de quién devolvió una orden ni de cuánto estuvo en curso; métricas de tiempo de resolución sesgadas (usan `created_at`).
7. **Incoherencia técnico–equipo** (punto 18). Impacto: dos fuentes de verdad (`teamType` del técnico y membresía); un técnico puede estar en varios equipos o en uno de otro tipo; las autorizaciones usan solo el atributo.
8. **Especialidad no usada en `take`** (punto 15). Impacto: un técnico electricista puede tomar una orden mecánica si su equipo atiende ese tipo.
9. **Comentario de cierre de 50 caracteres** (punto 8). Impacto: notas de cierre más pobres que las acordadas (200).
10. **JWT sin refresh ni revocación** (punto 2). Impacto: un token robado es válido hasta 60 minutos y no hay logout real; renovar sesión obliga a reautenticarse.
11. **`DB_PASSWORD` con default en el `application.yml` base** (punto 6). Impacto: un despliegue sin la variable arranca con la clave `enterpriselab`; el secreto JWT no valida la longitud en código.
12. **Despliegue como `root@` por SSH y acciones pinadas por tag** (punto 25). Impacto: depende de que el script del servidor sea restrictivo (no verificable); una acción comprometida en un tag mayor podría ejecutar código con acceso al secreto SSH.

### Baja
13. Dockerfile sin `HEALTHCHECK` ni digest de imagen; compose expone `5432` al host.
14. Sin análisis de vulnerabilidades de dependencias ni Dependabot; `nimbus-jose-jwt 9.37.4` antiguo.
15. Agentes del flujo SDD fuera del repo (`~/.claude/agents`) y archivo `spec-verifier.md.bak` suelto.
16. Seeds intercalados en la numeración de Flyway (`V1_1`…) y `outOfOrder` desactivado.
17. 401/403 sin anotar en OpenAPI; versión de la API fija en `0.1.0`.
18. Reintentos agotados en `attempt()` → `IllegalStateException` → 500 en lugar de 409.

---

## Recomendaciones

### Alta
1. **Bloquear edición y baja de órdenes cerradas.** Rechazar `PUT`/`DELETE` si `status` es `completed` o `cancelled` (409, p. ej. `WORK_ORDER_CLOSED`) y respaldarlo en el `UPDATE` con `AND status IN ('pending','in-progress')`. Archivos: `J/workorders/domain/WorkOrderService.java` (`update`, `delete`), `J/workorders/persistence/WorkOrderRepositoryAdapter.java`; tests en `T/workorders/web/WorkOrderControllerIT.java`. Actualizar README y la spec 03.
2. **Rate limiting / bloqueo en `POST /auth/login`.** Filtro por IP y username (p. ej. Bucket4j o contador en BD con bloqueo temporal) devolviendo 429 con `ApiError`. Archivos: `J/auth/security/SecurityConfig.java` (o un filtro nuevo en `auth/security`), `J/auth/domain/AuthService.java`; test en `T/auth/web/AuthControllerIT.java`.
3. **Deshabilitar Swagger/OpenAPI fuera de `dev`.** `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.enabled=false` en el `application.yml` base, activados en el bloque `dev`; quitar los `permitAll` equivalentes o condicionarlos. Archivos: `R/application.yml`, `J/auth/security/SecurityConfig.java`; test en `T/auth/security/SecurityConfigIT.java`.
4. **Definir cómo se crea el primer usuario y los siguientes en producción** (endpoint de alta de usuarios para admin con validación de legajo para `tecnico`, o un procedimiento de bootstrap documentado con variable de entorno). Archivos: nuevo caso de uso en `J/auth/domain/` y `UserRepository`, controller en `J/auth/web/`, `README.md`.

### Media
5. **Mapear las excepciones de Spring MVC** (`HttpRequestMethodNotSupportedException` → 405, `HttpMediaTypeNotSupportedException` → 415, `MissingServletRequestParameterException`/`MethodArgumentTypeMismatchException` → 400) con `ApiError`. Archivo: `J/shared/web/RestExceptionHandler.java`; test en `T/shared/web/RestExceptionHandlerTest.java`.
6. **Subir el mínimo del comentario a 200** (y alinear README, `DESCRIPTION`/OpenAPI y specs) o formalizar el 50 como decisión nueva. Archivos: `J/workorders/domain/WorkOrderFlowService.java:36`, `README.md`, `.claude/specs/04-ordenes-flujo/*`.
7. **Agregar `start_date`/`end_date` (o exponer `taken_at`/`closed_at` con esos nombres) y calcular el promedio desde el inicio.** Conservar la fecha de inicio al `release` (o guardar el historial). Archivos: nueva migración `R/db/migration/V10__…sql`, `J/workorders/persistence/WorkOrderEntity.java`, `WorkOrderMapper.java`, `web/WorkOrderResponse.java`, `J/dashboard/persistence/DashboardStatisticsAdapter.java:38`.
8. **Registrar el `release`** (quién y cuándo), p. ej. tabla `work_order_events`. Archivos: migración nueva, `J/workorders/domain/WorkOrderFlowService.java:90-100`, `WorkOrderRepository`.
9. **Unificar la cancelación**: decidir si `/cancel` exige comentario y si admin/TL pueden cancelar `in-progress`. Archivos: `J/workorders/domain/WorkOrderFlowService.java:102-112`, `WorkOrderController.java:193`.
10. **Aplicar la especialidad en `take`** (si es la decisión vigente) con una función `canTechnicianHandle(specialty, teamType, orderType)`. Archivos: `J/workorders/domain/WorkOrderPermissions.java:76`, `WorkOrderFlowService.java:55`.
11. **Coherencia de equipos**: validar que `team.type == technician.teamType` y limitar a un equipo por técnico (constraint `UNIQUE (technician_id)` en `team_members` y validación en `TeamService`). Archivos: `J/maintenance/domain/TeamService.java:125-152`, migración nueva.
12. **Quitar el default de `DB_PASSWORD`** fuera de `dev` y validar longitud mínima del `JWT_SECRET` al arrancar. Archivos: `R/application.yml:7`, `J/auth/security/JwtProperties.java`.

### Baja
13. Agregar `HEALTHCHECK`, `-XX:+ExitOnOutOfMemoryError` y digest de imagen en `Dockerfile`; no publicar `5432` en `docker-compose.yaml` si no hace falta.
14. Configurar Dependabot y un escaneo (OWASP Dependency-Check o `gradle dependencyCheck`) en `.github/workflows/ci.yml`; revisar `nimbus-jose-jwt` y `springdoc`.
15. Versionar los agentes del flujo SDD en `.claude/agents/` y eliminar `spec-verifier.md.bak`.
16. Anotar 401/403 en OpenAPI (respuesta global en `J/shared/web/OpenApiConfig.java`).
17. Convertir el agotamiento de reintentos en un `409`/`503` controlado. Archivo: `J/workorders/domain/WorkOrderFlowService.java:133`.
18. Pinar las acciones de GitHub por SHA y revisar el uso de `root@` en el deploy.

---

## Qué no pude verificar y por qué

- **Vulnerabilidades conocidas (CVE) de las dependencias**: no se ejecutó ningún escáner ni se instaló nada nuevo; solo se listaron versiones.
- **Comportamiento real de 405/415/parámetros inválidos**: lo deduje de `RestExceptionHandler` y de la ausencia de tests; no levanté la aplicación para comprobarlo con peticiones reales.
- **Matriz exhaustiva de 403 por endpoint y rol**: revisé la existencia de tests por módulo, no cada caso uno por uno.
- **Script de deploy y rollback del VPS** (`api <tag>`): vive en el servidor, fuera del repo; no se puede verificar el rollback automático ni la restricción del comando SSH.
- **Ejecución del workflow en GitHub Actions** y el resultado del CI en un PR: no tengo acceso al estado remoto (`CLAUDE.md` lo declara pendiente).
- **Variables y secretos reales de producción** (`JWT_SECRET`, `DB_PASSWORD`, `app.cors.allowed-origins`, `DEPLOY_ENABLED`): no están en el repo.
- **Contenido íntegro de algunos archivos** no leídos línea por línea: entidades y mappers JPA, `DashboardService`, `MachineService`/`PartService`, controllers de `maintenance`/`machines`, el cuerpo de los tests, `.claude/specs/*/design.md` y `requirements.md`. Las conclusiones sobre ellos se basan en búsquedas dirigidas y en los resultados de los tests.
- **Las "decisiones acordadas"** (200 caracteres, solo lectura tras cierre, `startDate`/`endDate`, `canTechnicianHandle`) no están escritas en el repo; las comparé con el enunciado de la auditoría. No encontré el nombre `canTechnicianHandle` en el backend (probablemente existe en el frontend, que no auditamos).
- **Versión exacta de `nimbus-jose-jwt` más reciente** y su estado de seguridad: sin acceso a la red para consultarlo.
