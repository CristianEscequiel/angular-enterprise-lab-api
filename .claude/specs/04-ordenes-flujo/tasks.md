# Tareas: Spec 04 — Órdenes de trabajo: tomar, cerrar y liberar

Entorno: compilar requiere JDK 21 (`JAVA_HOME`) y Docker para las IT. Toda tarea
deja `./gradlew test` en verde. Las IT comparten un único Postgres (ver
`design.md` §7): cada test crea sus propias órdenes por la API de la spec 03 con
valores únicos (título con prefijo propio, p. ej. `WOFIT-`), las limpia al
terminar y no afirma sobre totales sino sobre lo que contiene. **Las 32 órdenes
del seed solo se leen.** La migración de esta spec es `V8`.

- [x] 1. `ConflictException` con `details`
  - Detalle: `shared/domain/ConflictException` suma un constructor
    `(code, message, Map<String,String> details)` y `details()` (vacío si no hay);
    `RestExceptionHandler.handleConflict` los pasa al `ApiError` y los omite si
    están vacíos o son nulos (design.md §1, conflictos). Es el único cambio fuera
    de `workorders`.
  - Depende de: —
  - Verificación: `RestExceptionHandlerTest` — `409` con `details` los serializa;
    sin `details` (o vacíos) el cuerpo no trae el campo. Las IT de `409` de las
    specs 01 y 02 siguen en verde sin cambios.
  - _Requisitos: habilita REQ-6, REQ-17, REQ-18, REQ-24_

- [x] 2. Migración `V8` y `WorkOrdersStateInvariantsMigrationIT`
  - Detalle: `db/migration/V8__work_orders_state_invariants.sql` con los tres
    `CHECK` de design.md §2 (dos de grupo y el de invariantes por estado). No
    modifica filas.
  - Depende de: —
  - Verificación: `WorkOrdersStateInvariantsMigrationIT`, por SQL directo —
    rechazadas: `pending` con dueño, `pending` con nota, `in-progress` sin dueño,
    `in-progress` con nota, cerrada sin dueño, sin nota y con autor distinto del
    dueño, dueño y nota incompletos (p. ej. dueño con `taken_at` nulo); aceptadas:
    una combinación válida de cada estado. Una base propia con `V7` + `V7_1`
    migrada hasta `V8` conserva las 32 filas sin cambios. `SeedWorkOrdersIT`,
    `WorkOrdersMigrationIT` y `MigrationIT` siguen en verde.
  - _Requisitos: REQ-30_

- [x] 3. Permisos de las transiciones
  - Detalle: `WorkOrderPermissions` suma `requireTake(rol)` y `requireClose(rol)`
    (solo `TECNICO`), `requireRelease(rol)` (`ADMINISTRADOR` y
    `TEAM_LEADER_MANTENIMIENTO`) y `requireTeamServes(TeamType, WorkOrderType)`
    (`guardia` → `pronto-intervencion`; `preventivo-correctivo` → `preventivo` y
    `correctivo`). Reutiliza `AccessPolicy.requireRole` y
    `maintenance.domain.TeamType`.
  - Depende de: —
  - Verificación: `WorkOrderPermissionsTest` — matriz de los cuatro roles para
    `requireTake`, `requireClose` y `requireRelease`; matriz **2 equipos × 3 tipos**
    de `requireTeamServes`; rol y equipo `null` dan `403`.
  - _Requisitos: REQ-3, REQ-4, REQ-15, REQ-23_

- [x] 4. Puerto y persistencia de las transiciones
  - Detalle: `WorkOrderRepository` suma `take(id, TakenBy)`, `close(id, ownerId,
    outcome, ClosingNote)` y `release(id)`, cada una `Optional<WorkOrder>` (vacío
    si el `UPDATE` tocó 0 filas). `WorkOrderJpaRepository` suma los tres
    `@Modifying(flushAutomatically = true, clearAutomatically = true)` con el
    `WHERE` de design.md §3. `WorkOrderRepositoryAdapter` los corre cada uno en una
    transacción: `UPDATE` → si `1`, `findById` y mapeo. **`WorkOrderEntity` no
    cambia: conserva `updatable = false`**; se le agrega un comentario que explica
    que lo escriben solo las consultas `@Modifying` (design.md §1).
  - Depende de: 2
  - Verificación: `WorkOrderRepositoryAdapterIT` — cada operación escribe las
    columnas y la relectura las ve; 0 filas → vacío para estado equivocado, dueño
    distinto (`close`) e id inexistente; `release` deja dueño y fecha en `null`; una
    entity leída antes de un `close` y guardada con `save` después **no pisa**
    estado, dueño ni nota (REQ-28); ninguna operación rompe un `CHECK` de `V8`.
  - _Requisitos: REQ-7, REQ-20, REQ-21, REQ-22, REQ-28, REQ-29_

- [x] 5. `WorkOrderFlowService.take`
  - Detalle: en `workorders/domain`, `WorkOrderFlowService(AuthService,
    WorkOrderRepository, Clock)` con `take(username, id)`: `currentUser` (`401`) →
    `requireTake` (`403`) → `NumericId` y `findById` (`404`) → `requireTeamServes`
    con `TeamType.fromValue(user.teamType())` (`403`) → estado `pending` (`409
    WORK_ORDER_NOT_PENDING`) → `repository.take` con `TakenBy(user.id(),
    displayName, ahora truncado a milisegundos)`. Un resultado vacío reevalúa desde
    `findById` hasta 3 intentos; se agotan → `IllegalStateException`. Incluye el
    ayudante que arma `details` (`status` y, si hay dueño, `takenById` y
    `takenByName`), compartido con las tareas 6 y 7.
  - Depende de: 1, 3, 4
  - Verificación: `WorkOrderFlowServiceTest` (Mockito sobre los puertos, `Clock`
    fijo) — alta de dueño con id, nombre y hora del usuario de la base; rol leído
    de la base y no del token; usuario inexistente → `UnknownSessionUserException`;
    orden de errores `401 → 403 rol → 404 → 403 equipo → 409`; `403` de equipo
    antes que `409`; `409` con `details` para `in-progress` propia, ajena,
    `completed` y `cancelled`; id no numérico → `404`; 0 filas y la relectura ya no
    está `pending` → `409` con el estado real; 0 filas y vuelve a estar `pending`
    → reintenta; 3 intentos agotados → `IllegalStateException`; `take` no consulta
    otras órdenes del técnico.
  - _Requisitos: REQ-1 a REQ-8, REQ-26, REQ-27_

- [x] 6. `WorkOrderFlowService.close`
  - Detalle: `CloseWorkOrderCommand(String outcome, String comment)` y
    `close(username, id, command)`: `currentUser` → `requireClose` → validación de
    `outcome` (obligatorio; solo `completed` o `cancelled` con coincidencia exacta)
    y `comment` (obligatorio; `strip()`, 50 a 500 caracteres; todos los errores
    juntos como `ValidationFailedException`) → `findById` (`404`) →
    `requireTeamServes` (`403`) → estado `in-progress` (`409
    WORK_ORDER_NOT_IN_PROGRESS`) → dueño (`409 WORK_ORDER_TAKEN_BY_OTHER`) →
    `repository.close` con el comentario recortado y el autor del usuario; mismo
    reintento que `take`. Un comando con campos nulos es válido como entrada.
  - Depende de: 5
  - Verificación: `WorkOrderFlowServiceTest` — `outcome` ausente, `pending`,
    `in-progress`, `Completed` y `""` → error del campo; `comment` ausente, vacío,
    de espacios, 49, 10 letras entre 60 espacios → error; 50 y 500 aceptados, 501
    error; todos los errores juntos; el comentario se guarda recortado; autor y
    hora del usuario y del `Clock`; `403` de rol sobre `400`, `400` sobre `404`,
    `404` sobre `403` de equipo, `403` de equipo sobre `409`; `NOT_IN_PROGRESS`
    antes que `TAKEN_BY_OTHER` (una orden cerrada por otro técnico es
    `NOT_IN_PROGRESS`); `details` con dueño; 0 filas y reintento como en `take`.
  - _Requisitos: REQ-9 a REQ-21, REQ-27_

- [x] 7. `WorkOrderFlowService.release`
  - Detalle: `release(username, id)`: `currentUser` → `requireRelease` → `findById`
    (`404`) → estado `in-progress` (`409 WORK_ORDER_NOT_IN_PROGRESS` con
    `details`) → `repository.release`; mismo reintento. No registra quién liberó.
  - Depende de: 5
  - Verificación: `WorkOrderFlowServiceTest` — administrador y team leader liberan;
    `tecnico` (incluido el dueño) y `produccion` → `403` aun sobre una orden en el
    estado equivocado; id inexistente y no numérico → `404`; `pending`, `completed`
    y `cancelled` → `409` con `status` (y dueño cuando lo hay); 0 filas y relectura
    como en `take`.
  - _Requisitos: REQ-22 a REQ-25, REQ-27_

- [x] 8. Endpoints `take`, `close` y `release`
  - Detalle: en `WorkOrderController`, `POST /{id}/take` y `/release` (sin leer el
    cuerpo) y `POST /{id}/close` con `WorkOrderCloseRequest(String outcome, String
    comment)` y `@RequestBody(required = false)` (sin cuerpo → comando con campos
    nulos). Los tres pasan `jwt.getSubject()` y el id de la URL al servicio y
    devuelven `200 WorkOrderResponse`. Anotaciones OpenAPI con `403`, `404`, `409`
    (y `400` en `close`).
  - Depende de: 6, 7
  - Verificación: `WorkOrderFlowControllerIT` (perfil `dev`; usuarios `tecnico`,
    `electricista`, `teamleader`, `admin`, `produccion`) — `take` del técnico
    habilitado → `200`, `in-progress`, `takenBy` con id, nombre y hora, y con un
    `takenBy` ajeno en el cuerpo queda el del token; `403` y orden intacta para
    guardia sobre `preventivo`/`correctivo`, para `preventivo-correctivo` sobre
    `pronto-intervencion` y para los otros tres roles; cambiar el equipo del técnico
    en el maestro cambia la respuesta sin reemitir el token; `404` en los tres verbos
    con id inexistente y no numérico; `409` con `details` al tomar `in-progress`
    propia, ajena, `completed` y `cancelled`; un técnico toma dos órdenes y ambas
    quedan a su nombre; `close` con `completed` y `cancelled` (comentario recortado,
    `takenBy` intacto, autor del token aunque el cuerpo traiga otro); `400` con el
    campo y la orden intacta para `outcome` y `comment` inválidos; `close` sin
    cuerpo de `produccion` → `403` y de un técnico → `400`; `403` de rol y de equipo;
    `409` `NOT_IN_PROGRESS` y `TAKEN_BY_OTHER` con `details`; `take`, `close` y
    `release` sobre cerradas → `409` y nada cambia; `release` de administrador y
    team leader → `pending`, `takenBy` `null`, sin `closingNote`; `403` a `tecnico`
    (dueño incluido) y `produccion`; `409` al liberar `pending` y cerradas; liberar y
    retomar con el mismo técnico y con otro; `PUT` con `status`, `takenBy` y
    `closingNote` sobre una orden que pasó por `take` los conserva; después de cada
    transición, `GET /work-orders/{id}`, el listado y `?status=` la reflejan; orden de
    errores (`403` sobre `400`, `400` sobre `404`, `404` sobre `403` de equipo); sin
    token → `401`; las 32 órdenes del seed no se modifican.
  - _Requisitos: REQ-1 a REQ-6, REQ-8 a REQ-19, REQ-22 a REQ-29_

- [x] 9. Pruebas de concurrencia
  - Detalle: ayudante de prueba que crea por SQL un segundo técnico de guardia
    (`users` con el hash de un usuario del seed y su fila en `technicians`) y lo
    borra al terminar. `WorkOrderFlowConcurrencyIT` dispara las operaciones con
    `CountDownLatch` desde hilos distintos contra el Postgres real.
  - Depende de: 8
  - Verificación: `WorkOrderFlowConcurrencyIT` — dos `take` de dos técnicos sobre la
    misma `pending`: exactamente un `200` y un `409` con el dueño real, repetido 20
    veces; dos `close` simultáneos del mismo técnico con comentarios distintos: un
    `200`, un `409 WORK_ORDER_NOT_IN_PROGRESS` y la nota guardada es la del `200`;
    `close` del dueño contra `release` del team leader, 20 repeticiones, simultáneos y
    con el cierre después de la liberación (incluido el caso en que otro técnico
    retomó la orden): nunca una `pending` con nota ni una `in-progress` con nota,
    siempre un único ganador y el cierre del dueño anterior no se aplica sobre la
    orden retomada.
  - _Requisitos: REQ-7, REQ-20, REQ-21_

- [x] 10. Documentación OpenAPI y `OpenApiIT`
  - Detalle: se amplía `OpenApiIT`; se revisa que los códigos de cada operación salgan
    de las anotaciones y no de un texto a mano.
  - Depende de: 8
  - Verificación: `OpenApiIT` — `/v3/api-docs` contiene `/work-orders/{id}/take`,
    `/close` y `/release` con `post` y `bearerAuth`, cada uno con `401`, `403`, `404`
    y `409`, y `400` en `close`; el esquema del cuerpo de `close` tiene `outcome` y
    `comment`.
  - _Requisitos: REQ-31_

- [x] 11. Cierre: README, estado actual y recorrido de requisitos
  - Detalle: README con los tres endpoints, el formato del `409` con `details` y un
    ejemplo de flujo (tomar → cerrar, liberar); actualizar "Estado actual" de
    `CLAUDE.md` (migraciones: la 04 usa `V8`) y el estado de la spec 04 en
    `ROADMAP.md`; agregar al final de este archivo el recorrido de REQ-1 a REQ-31
    con su evidencia y los desvíos respecto del diseño, como en las specs 01 a 03.
  - Depende de: 1 a 10
  - Verificación: `./gradlew test` completo en verde; cada REQ con un test que lo
    cubra en el recorrido.
  - _Requisitos: REQ-1 a REQ-31_

## Recorrido de REQ-1 a REQ-31

Verificado con `./gradlew test` completo en verde (2026-10-01). Abreviaturas:
WFST = `WorkOrderFlowServiceTest`, WFIT = `WorkOrderFlowControllerIT`, WCCIT =
`WorkOrderFlowConcurrencyIT`, WPT = `WorkOrderPermissionsTest`, WRA =
`WorkOrderRepositoryAdapterIT`, WSIMIT = `WorkOrdersStateInvariantsMigrationIT`,
OAIT = `OpenApiIT`.

| REQ | Evidencia |
|---|---|
| 1, 2 | WFST (alta de dueño con id, nombre y hora del usuario y del `Clock`); WFIT (`takenBy` del token aunque el cuerpo traiga otro) |
| 3, 4 | WPT (matriz 4 roles y 2 equipos × 3 tipos); WFIT (`403` y orden intacta por equipo y por rol; cambio de equipo en el maestro sin reemitir el token) |
| 5, 16, 25 | WFST y WFIT: id inexistente y no numérico → `404` en los tres verbos |
| 6, 19 | WFST y WFIT: `take` sobre `in-progress` propia, ajena, `completed` y `cancelled` → `409` con `details`; `take`, `close` y `release` sobre cerradas no cambian nada |
| 7 | WCCIT: dos `take` simultáneos, un `200` y un `409` con el dueño real, 20 veces; WFST (0 filas y relectura) |
| 8 | WFIT: un técnico toma dos órdenes; WFST (`take` no consulta otras órdenes) |
| 9, 10 | WFIT: `completed` y `cancelled`, comentario recortado, `takenBy` intacto, autor del token; WFST |
| 11, 12, 13, 14 | WFST (todos los casos límite y errores juntos); WFIT (`400` con el campo y la orden intacta; límites 50 y 500) |
| 15 | WFST y WFIT: `close` de rol no técnico y de equipo equivocado → `403` |
| 17, 18 | WFIT y WFST: `NOT_IN_PROGRESS` y `TAKEN_BY_OTHER` con `details`; cerrada por otro es `NOT_IN_PROGRESS` |
| 20 | WCCIT: dos `close` del mismo técnico, un `200`, un `409`, la nota guardada es la del `200` |
| 21 | WCCIT: `close` contra `release` (20 veces) y cierre tardío tras liberar y tras retomar otro técnico |
| 22, 23, 24 | WFIT y WFST: liberación de administrador y team leader; `403` a técnico (dueño incluido) y producción; `409` sobre `pending` y cerradas |
| 26 | WFIT: liberar y retomar con el mismo y con otro técnico |
| 27 | WFST y WFIT: `403` de rol sobre `400`, `400` sobre `404`, `404` sobre `403` de equipo, `403` de equipo sobre `409` |
| 28 | WRA (entity vieja guardada tras un cierre no pisa estado, dueño ni nota); WFIT (`PUT` sobre una orden tomada) |
| 29 | WFIT: detalle, listado y `?status=` reflejan cada transición; WRA |
| 30 | WSIMIT: cada combinación inválida y válida por SQL; una base propia `V7`+`V7_1` migrada a `V8` conserva las 32 filas |
| 31 | OAIT: tres rutas `post` con `bearerAuth`, `401`, `403`, `404`, `409` (y `400` en `close`) y el esquema `WorkOrderCloseRequest` |

Desvíos respecto del diseño:

- **`V8`: el `CHECK` de invariantes agrega `closing_author_id IS NOT NULL`.** El
  diseño §2 afirmaba que `closing_author_id = taken_by_id` es falso si alguno es
  nulo, pero en SQL es `NULL`, y un `CHECK` acepta `NULL`: una orden cerrada con
  dueño y sin nota habría pasado. WSIMIT lo detectó. El comportamiento queda el de
  REQ-30 literal.
- `V8` obliga a corregir tres tests de la spec 03 que forzaban por SQL estados
  incoherentes (`WorkOrdersMigrationIT`, `WorkOrderRepositoryAdapterIT` y el
  `force` de `WorkOrderControllerIT`): ahora cargan dueño y nota en un solo `UPDATE`.
- Las tareas 5 a 7 comparten `WorkOrderFlowService` y se implementaron juntas.
- Se agregó el ayudante de test `SecondGuardiaTechnician` (tarea 9), usado también
  por `WorkOrderFlowControllerIT` para los `409` por dueño ajeno.
- La política de "más de 3 intentos" del diseño §1 es `MAX_ATTEMPTS = 3`: tras el
  tercer `UPDATE` de 0 filas lanza `IllegalStateException` (`500`), como se decidió.

---

# Tareas: Enmienda 04-A — Cancelar una orden pendiente

Cubre REQ-32 a REQ-48 con el diseño de `design.md` §10 a §17. Mismas reglas de
entorno y de IT que arriba (prefijo propio de títulos, p. ej. `WOCIT-`; las 32
órdenes del seed solo se leen). La migración de la enmienda es `V9`. **Los tests
que ya existen no cambian** (design.md §16.5); esta enmienda solo agrega casos y
clases. **Convención de nombres:** cada método de test nuevo lleva el id del criterio
que verifica en el nombre (p. ej. `req32_cancelPendingOrderReturnsCancelled`), para que
`spec-verifier` lo encuentre. Las tareas 12 a 14 son independientes entre sí. Orden:
12 → 15 → 16 → 17 → 18 y 19 → 20.

- [x] 12. Migración `V9` y `WorkOrdersCancelMigrationIT`
  - Detalle: `db/migration/V9__work_orders_cancel_pending.sql` con el SQL de
    design.md §11 (reemplaza `work_orders_closing_group_check` y
    `work_orders_state_invariants_check`; deja `work_orders_owner_group_check`). No
    modifica filas.
  - Depende de: —
  - Verificación: `WorkOrdersCancelMigrationIT` (paquete `com.enterpriselab.api`,
    con `AbstractPostgresIT`), por SQL directo — aceptada: `cancelled` sin dueño,
    con autor, nombre y `closed_at`, sin comentario; rechazadas: todas las
    combinaciones de REQ-47. Una base propia con `V7` + `V7_1` migrada hasta `V9`
    conserva las 32 filas. `WorkOrdersStateInvariantsMigrationIT`,
    `SeedWorkOrdersIT`, `WorkOrdersMigrationIT` y `MigrationIT` siguen en verde sin
    cambios.
  - _Requisitos: REQ-41, REQ-47_

- [x] 13. `WorkOrderMapper`: la `closingNote` depende del comentario
  - Detalle: `WorkOrderMapper.toDomain` arma la `ClosingNote` solo si
    `closing_comment` no es nulo (hoy decide `closing_author_id`). `toNewEntity` no
    cambia.
  - Depende de: —
  - Verificación: `WorkOrderMapperTest` (**nuevo**, paquete
    `com.enterpriselab.api.workorders.persistence`) — fila con autor y sin comentario
    → `closingNote` nula; con comentario → nota completa; sin autor → nula. Las
    pruebas de cierre existentes siguen en verde (con comentario, la `closingNote` del
    cierre del técnico sale completa).
  - _Requisitos: REQ-32, REQ-42, REQ-43, REQ-44_

- [x] 14. Permiso `requireCancel`
  - Detalle: `WorkOrderPermissions.requireCancel(Role)` con
    `AccessPolicy.requireRole(current, ADMINISTRADOR, TEAM_LEADER_MANTENIMIENTO)`.
    Método propio, sin reutilizar `requireRelease`.
  - Depende de: —
  - Verificación: `WorkOrderPermissionsTest` — matriz de los cuatro roles (administrador
    y team leader pasan; técnico y producción dan `ForbiddenOperationException`) y rol
    `null` da `403`.
  - _Requisitos: REQ-34, REQ-38_

- [x] 15. Puerto y persistencia de `cancel`
  - Detalle: record `Cancellation(long authorId, String authorName, Instant at)` en
    `workorders/domain`; `WorkOrderRepository.cancel(long id, Cancellation by)` →
    `Optional<WorkOrder>` (vacío si el `UPDATE` tocó 0 filas);
    `WorkOrderJpaRepository.cancel` con el `@Modifying(flushAutomatically = true,
    clearAutomatically = true)` de design.md §12; `WorkOrderRepositoryAdapter.cancel`
    en una transacción: `UPDATE` → si `1`, relectura. `WorkOrderEntity` no cambia.
  - Depende de: 12, 13
  - Verificación: `WorkOrderRepositoryAdapterIT` (se amplía) — `cancel` sobre una
    `pending` devuelve la orden `cancelled` con `takenBy` nulo, sin `closingNote` y
    con `closing_author_*` y `closed_at` escritos; sobre una que ya no está `pending`
    o inexistente → `Optional.empty()` y la fila no cambia; una entity leída antes del
    `cancel` y guardada con `save` después no pisa estado ni autor; ninguna operación
    rompe un `CHECK` de `V9`.
  - _Requisitos: REQ-32, REQ-40, REQ-42_

- [x] 16. `WorkOrderFlowService.cancel`
  - Detalle: `cancel(username, id)` según design.md §12: `currentUser` (`401`) →
    `requireCancel` (`403`) → `attempt` con `NumericId` y `findById` (`404`) → estado
    `pending` (`409 WORK_ORDER_NOT_PENDING` con `conflict(...)`) → `orders.cancel` con
    `Cancellation(user.id(), user.displayName(), now())`. Un resultado vacío
    reevalúa hasta 3 intentos. Actualizar el Javadoc de la clase para incluir
    `cancel`.
  - Depende de: 14, 15
  - Verificación: `WorkOrderFlowServiceTest` (se amplía) — éxito sobre `pending` con
    `Clock` fijo (el puerto recibe el id, el usuario del token y la hora truncada);
    `in-progress`, `completed` y `cancelled` → `ConflictException`
    `WORK_ORDER_NOT_PENDING` con `details` (`status` y, si hay dueño, `takenById` y
    `takenByName`) y sin llamar a `orders.cancel`; técnico y producción → `403` sin
    leer la orden; id inexistente y no numérico → `404`; `cancel` vacío y luego
    `cancelled` en la relectura → `409` con el estado real.
  - _Requisitos: REQ-32, REQ-34, REQ-36, REQ-37, REQ-38, REQ-39, REQ-48_

- [x] 17. Endpoint `POST /work-orders/{id}/cancel` y OpenAPI
  - Detalle: `WorkOrderController.cancel(@AuthenticationPrincipal Jwt, @PathVariable
    String id)` sin `@RequestBody`, con `@Operation` y `@ApiResponses` (`200`, `404`,
    `409`) según design.md §12. `SecurityConfig` no cambia.
  - Depende de: 16
  - Verificación: `WorkOrderFlowControllerIT` (se amplía) — administrador y team leader
    → `200`, `cancelled`, `takenBy` nulo, sin `closingNote`, demás campos iguales; con
    cuerpo con `status`/`comment`, con JSON malformado y con `text/plain` → mismo `200`;
    `tecnico` (incluido el que la tomó) y `produccion` → `403` con la orden intacta, y
    también sobre una orden `in-progress` o un id inexistente; sin token y con token
    inválido → `401`; inexistente y no numérico → `404`; `in-progress`, `completed` y
    `cancelled` → `409` con `details`; sobre una cancelada, `cancel` y `take`
    (técnico habilitado) → `409 WORK_ORDER_NOT_PENDING` sin `takenById`, `close`
    (técnico habilitado) y `release` → `409 WORK_ORDER_NOT_IN_PROGRESS`; `close` con
    `outcome: cancelled` sigue exigiendo comentario y devuelve la `closingNote`;
    detalle, listado y `?status=cancelled` reflejan la cancelación; SQL directo:
    `closing_author_id`/`closing_author_name` del token y `closed_at` del reloj.
    `OpenApiIT` (se amplía el bucle a `cancel`) — ruta `post` con `bearerAuth`,
    `401`, `403`, `404`, `409` y sin esquema de cuerpo.
  - _Requisitos: REQ-32, REQ-33, REQ-34, REQ-35, REQ-36, REQ-37, REQ-38, REQ-39,
    REQ-42, REQ-43, REQ-44, REQ-46, REQ-48_

- [x] 18. Concurrencia de `cancel`
  - Detalle: sin código nuevo; fija que la actualización condicional de la tarea 15
    deja un solo ganador.
  - Depende de: 17
  - Verificación: `WorkOrderFlowConcurrencyIT` (se amplía), 20 repeticiones cada uno —
    `cancel` del team leader contra `take` del técnico habilitado, y dos `cancel` a la
    vez: exactamente un `200` y un `409` con el estado real; nunca una `cancelled` con
    dueño ni una `in-progress` sin dueño (consulta SQL del estado final).
  - _Requisitos: REQ-40_

- [x] 19. Dashboard cuenta la cancelación
  - Detalle: sin código de producción (design.md §10, dashboard); solo pruebas que lo
    fijan. Si alguna falla, se informa antes de tocar `dashboard` (la decisión es del
    usuario).
  - Depende de: 12, 17
  - Verificación: `DashboardControllerIT` (se amplía) — cancelar una `pending` por la
    API y pedir `GET /dashboard/summary` con un período que incluya el instante:
    `closedInPeriod.cancelled` y `closedInPeriod.total` suben en 1, `byStatus.cancelled`
    sube en 1, `byStatus.pending` y `open` bajan en 1, el `total` raíz y
    `averageResolutionMinutes` no cambian, `GET /dashboard/workload` no la incluye; con
    un período anterior no la cuenta; sin `500`. `DashboardStatisticsAdapterIT` (se
    amplía) — `snapshot` cuenta en `ClosedStats.cancelled` una `cancelled` sin dueño ni
    comentario con `closed_at` dentro del período.
  - _Requisitos: REQ-45_

- [x] 20. Suite completa y recorrido de REQ-32 a REQ-48
  - Detalle: correr `./gradlew test` completo; agregar al final de este archivo el
    recorrido de REQ-32 a REQ-48 con su evidencia y los desvíos, como hizo la spec
    original. Informar cualquier test previo en rojo. Proponer al usuario (no
    aplicar sin su aprobación) la actualización del README (endpoint `cancel`) y de
    la sección "Estado actual" de `CLAUDE.md` (enmienda 04-A, y que las próximas
    migraciones numeran desde `V10`).
  - Depende de: 12, 13, 14, 15, 16, 17, 18, 19
  - Verificación: `./gradlew test` pasa; cada requisito de la enmienda tiene al menos un
    test con su id en el nombre o en la tabla del recorrido.
  - _Requisitos: REQ-32 a REQ-48_

## Cobertura de la enmienda

| REQ | Tareas |
|---|---|
| 32 | 13, 15, 16, 17 |
| 33 | 17 |
| 34, 38 | 14, 16, 17 |
| 35 | 17 |
| 36, 37 | 16, 17 |
| 39, 48 | 16, 17 |
| 40 | 15, 18 |
| 41, 47 | 12 |
| 42 | 13, 15, 17 |
| 43, 44 | 13, 17 |
| 45 | 19 |
| 46 | 17 |

Fuera de esta repo, para avisarle al usuario y no tocar: `verify-contract.mjs` del
frontend (`../angular-enterprise-lab/.claude/specs/018-integracion-api-backend/`, spec 018, T11)
necesita sumar `POST /work-orders/{id}/cancel`.

## Recorrido de REQ-32 a REQ-48

Verificado con `./gradlew test` completo en verde (2026-10-19): 1123 tests, 0 fallos,
0 errores, 0 omitidos. Abreviaturas: WFST = `WorkOrderFlowServiceTest`, WFIT =
`WorkOrderFlowControllerIT`, WCCIT = `WorkOrderFlowConcurrencyIT`, WPT =
`WorkOrderPermissionsTest`, WRA = `WorkOrderRepositoryAdapterIT`, WMT =
`WorkOrderMapperTest`, WCMIT = `WorkOrdersCancelMigrationIT`, DCIT =
`DashboardControllerIT`, DSAIT = `DashboardStatisticsAdapterIT`, OAIT = `OpenApiIT`.

| REQ | Evidencia |
|---|---|
| 32 | WFIT `req32_administratorAndTeamLeaderCancel…`; WRA `req32_req42_cancelOfAPendingOrder…`; WMT `req32_req42_aRowWithAuthorAndWithoutComment…` |
| 33 | WFIT `req33_theBodyOfCancelIsIgnoredWhateverItIs` |
| 34 | WPT `req34_aMissingRoleIsForbiddenToCancel` (más la matriz de roles); WFIT `req34_techniciansEvenTheOwnerAndProduction…` |
| 35 | WFST `req35_cancelOfATokenWithAMissingUserIsUnauthorized`; WFIT `req35_cancelWithoutTokenOrWithAnInvalidToken…` |
| 36 | WFST `req36_…`; WFIT `req36_cancelOfAnUnknownOrNonNumericIdIsNotFound` |
| 37 | WFST `req37_…`; WFIT `req37_cancelOfAnOrderThatIsNotPending…` |
| 38 | WFIT `req38_theRoleIsCheckedBeforeTheStateAndBeforeTheExistence` |
| 39 | WFST `req39_…`; WFIT `req39_aCancelledOrderCannotBeCancelledAgainNorTaken…` |
| 40 | WCCIT (cancel contra take y dos cancel); WRA (entity vieja y 0 filas); WFST (carrera perdida y borrado) |
| 41 | WCMIT `req41_…` (cancelada sin dueño aceptada, combinaciones de `V8` intactas, 32 filas sin tocar) |
| 42 | WFIT `req42_cancelStoresTheTokenUserAndTheServerTime…`; WRA; WMT |
| 43 | WFIT `req43_theTechnicianCloseAsCancelled…`; WMT `req43_req44_aRowWithComment…` |
| 44 | WFIT `req44_theDetailTheListAndTheStatusFilter…`; WMT `req44_aRowWithoutAuthorNorComment…` |
| 45 | DCIT (período que incluye el instante y período pasado); DSAIT `req45_…` |
| 46 | OAIT `req46_apiDocsListTheCancelEndpoint…` |
| 47 | WCMIT `req47_…` (cinco tests, una por combinación rechazada) |
| 48 | WFST `req48_…`; WFIT `req48_aCancelledOrderCannotBeClosedNorReleased` |

Desvíos respecto del diseño: ninguno registrado. Un primer intento de la suite, sin
Docker activo, dio 52 fallos, todos en clases `*IT` (Testcontainers); con Docker
activo la suite pasó completa sin tocar código.
