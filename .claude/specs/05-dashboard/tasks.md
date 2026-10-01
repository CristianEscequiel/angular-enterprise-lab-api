# Tareas: Spec 05 — Dashboard: indicadores

Entorno: compilar requiere JDK 21 (`JAVA_HOME`) y Docker para las IT. Toda tarea
deja `./gradlew test` en verde. Las IT comparten un único Postgres con el resto de
la suite (ver `design.md` §5): las cifras exactas se prueban en una **base propia**
(`DashboardStatisticsAdapterIT`) y las IT HTTP no afirman sobre totales absolutos
sino sobre diferencias contra su propia consulta previa. Las 32 órdenes del seed
solo se leen. Esta spec **no tiene migración**.

- [x] 1. Dominio base: permisos, modelos y puerto
  - Detalle: en `dashboard/domain`, `DashboardPermissions` (`requireSummary(rol)`: los
    cuatro roles; `requireWorkload(rol)`: `ADMINISTRADOR` y
    `TEAM_LEADER_MANTENIMIENTO`; un rol `null` da `403`, con `AccessPolicy.requireRole`),
    `DashboardQuery(String from, String to)`, `Period(LocalDate from, LocalDate to)` con
    `fromInstant()` y `toExclusiveInstant()` (día siguiente a las `00:00:00Z`),
    `DashboardSummary`, `ClosedInPeriod`, `WorkloadItem` y el puerto
    `DashboardStatistics` con sus records `StatisticsSnapshot`, `OrderCount`,
    `ClosedStats` y `OwnerLoad` (design.md §3). Importa los enums de
    `workorders.domain`, nada de JPA ni de Spring Data.
  - Depende de: —
  - Verificación: `DashboardPermissionsTest` — matriz de los cuatro roles para
    `requireSummary` y `requireWorkload`; rol `null` → `403` en ambos. `PeriodTest` —
    `toExclusiveInstant` es el día siguiente a las `00:00Z` (también en fin de mes y de
    año).
  - _Requisitos: REQ-18, REQ-19; habilita REQ-7_

- [x] 2. `DashboardService.summary`
  - Detalle: `DashboardService(DashboardStatistics, Clock)` con `summary(rol, query)`:
    `requireSummary` (`403`) → resolver el período (design.md §4.1: ausentes y un solo
    extremo se completan, formato `\d{4}-\d{2}-\d{2}` estricto con `LocalDate.parse`,
    vacío inválido, `from` posterior a `to` → error en `from`, errores de formato de los
    dos parámetros juntos en un `ValidationFailedException`) → `snapshot` → armar
    `byStatus`, `byPriority` y `byType` con ceros y en el orden de los enums, `total`,
    `open`, `closedInPeriod` (con `total`) y el promedio redondeado a un decimal
    (`HALF_UP`) o `null`.
  - Depende de: 1
  - Verificación: `DashboardServiceTest` (Mockito sobre el puerto, `Clock` fijo) —
    período por defecto con `to` = hoy y `from` = hoy − 29; solo `from` (`to` = hoy); solo
    `to` (`from` = `to` − 29); `2026-9-1`, `2026-02-30`, `2026-13-01`, `abc`, vacío,
    `+20260-01-01` → error del parámetro; los dos mal → los dos en `details`; `from` >
    `to` y `from` futuro con `to` por defecto → error en `from`; `403` antes que `400` y
    sin llamar al puerto; ceros para valores sin órdenes y claves en el orden del enum;
    `total` y `open`; `closedInPeriod.total`; redondeo (`10.25` → `10.3`, `10.24` →
    `10.2`, `10.0` → `10.0`); promedio `null` cuando el puerto devuelve `null`; sin
    filas → todo en `0`; el puerto recibe `from` a las `00:00Z` y `to + 1` a las `00:00Z`.
  - _Requisitos: REQ-2 a REQ-5, REQ-7 a REQ-13_

- [x] 3. `DashboardService.workload`
  - Detalle: `workload(rol)`: `requireWorkload` → `inProgressByOwner()` → ordenar por
    cantidad descendente, nombre ascendente sin distinguir mayúsculas y `takenById` para
    desempatar (design.md §4.3).
  - Depende de: 1
  - Verificación: `DashboardServiceTest` — orden por cantidad; empate por nombre
    (`ana` antes que `Beto`, mayúsculas ignoradas); empate de nombre por `takenById`; lista
    vacía; `403` a `tecnico` y `produccion` sin llamar al puerto.
  - _Requisitos: REQ-15 a REQ-17, REQ-19_

- [x] 4. `DashboardStatisticsAdapter.snapshot`
  - Detalle: en `dashboard/persistence`, el adaptador con `JdbcTemplate`;
    `snapshot(from, toExclusive)` anotado `@Transactional(readOnly = true, isolation =
    REPEATABLE_READ)` con las dos consultas de design.md §1 (conteos por `status,
    priority, type`; cerradas del período con el promedio de `completed` en minutos sin
    redondear). Convierte con `fromValue`. Paquete-privado, solo se conoce el puerto.
  - Depende de: 1
  - Verificación: `DashboardStatisticsAdapterIT` sobre una base propia (migrada con
    `db/migration`, sin seed; mismo enfoque que `WorkOrdersUpgradeIT`) con órdenes
    insertadas por SQL — conteos por combinación; base vacía → sin filas, `completed` y
    `cancelled` en `0` y promedio `null`; cierres el día `from` a las `00:00:00Z` y el día
    `to` a las `23:59:59.999Z` entran, el día anterior y el posterior no; `completed` y
    `cancelled` por separado; dos `completed` de duraciones conocidas dan su promedio en
    minutos; una `cancelled` y una `completed` fuera del período no entran al promedio;
    período sin `completed` → promedio `null`. Con el seed en una segunda base propia y un
    período amplio: 32 órdenes, 9 `completed` y 2 `cancelled`, promedio no negativo.
  - _Requisitos: REQ-2 a REQ-7, REQ-11 a REQ-13_

- [x] 5. `DashboardStatisticsAdapter.inProgressByOwner`
  - Detalle: la consulta de design.md §3 (agrupa por `taken_by_id` entre las
    `in-progress`; el nombre es el de la toma más reciente).
  - Depende de: 4
  - Verificación: `DashboardStatisticsAdapterIT` — base vacía → lista vacía; dos técnicos
    con distinta cantidad; solo cuenta `in-progress` (no `pending` ni cerradas del mismo
    técnico); un técnico con dos tomas y nombres distintos → el de `taken_at` más reciente.
  - _Requisitos: REQ-15, REQ-17_

- [x] 6. Endpoints `summary` y `workload`
  - Detalle: `DashboardController` (`@RequestMapping("/dashboard")`) con `GET /summary`
    (`@RequestParam(required = false) String from, to`) y `GET /workload`; los dos pasan
    `AuthenticatedRole.from(jwt)` al servicio. DTOs `DashboardSummaryResponse`,
    `PeriodResponse`, `ClosedInPeriodResponse` y `WorkloadItemResponse(takenById String,
    takenByName, inProgress)`; `averageResolutionMinutes` sale como `null` explícito;
    `workload` es un arreglo JSON. Anotaciones OpenAPI (design.md §1).
  - Depende de: 2, 3, 4, 5
  - Verificación: `DashboardControllerIT` (perfil `dev`; usuarios `admin`, `teamleader`,
    `produccion`, `tecnico`) — los cuatro roles reciben `200` en `summary` con las ocho
    claves y los tres mapas completos; sin token → `401` en ambos; `workload` `200` para
    `admin` y `teamleader`, `403` para `produccion` y `tecnico`; `summary` coincide con
    `GET /work-orders?status=` y `?priority=` (`totalItems`) para cada estado y cada
    prioridad; `total` ≥ 32 y `open` = `pending` + `in-progress`; sin parámetros,
    `period.to` = hoy y `period.from` = hoy − 29; con `from` y `to` el período se devuelve
    igual; `400` con el parámetro para formato inválido y para `from` > `to`; `401` sobre
    `400`; las transiciones se reflejan en la consulta siguiente (crear una orden, `take` →
    `in-progress` +1 y `pending` −1, `close` → `closedInPeriod` +1 y `completed` +1,
    `release` en otra → `pending` +1); el técnico que tomó aparece en `workload` con su
    cantidad y nombre; las 32 órdenes del seed no se modifican.
  - _Requisitos: REQ-1, REQ-5, REQ-6, REQ-8, REQ-10, REQ-14, REQ-15, REQ-18, REQ-19_

- [x] 7. Documentación OpenAPI y `OpenApiIT`
  - Detalle: se amplía `OpenApiIT`; se revisa que los códigos salgan de las anotaciones.
  - Depende de: 6
  - Verificación: `OpenApiIT` — `/v3/api-docs` contiene `/dashboard/summary` (con los
    parámetros `from` y `to`, `get`, `bearerAuth`, `200`, `400`, `401`) y
    `/dashboard/workload` (`get`, `bearerAuth`, `200`, `401`, `403`).
  - _Requisitos: REQ-20_

- [x] 8. Cierre: README, estado actual y recorrido de requisitos
  - Detalle: README con los dos endpoints, la forma de `summary` y de `workload` y un
    ejemplo de `curl`; actualizar "Estado actual" de `CLAUDE.md` y el estado de la spec
    05 en `ROADMAP.md` (módulo `dashboard` hecho; sin migración); agregar al final de
    este archivo el recorrido de REQ-1 a REQ-20 con su evidencia y los desvíos respecto
    del diseño, como en las specs 01 a 04.
  - Depende de: 1 a 7
  - Verificación: `./gradlew test` completo en verde; cada REQ con un test que lo cubra en
    el recorrido.
  - _Requisitos: REQ-1 a REQ-20_

## Recorrido de REQ-1 a REQ-20

Verificado con `./gradlew test` completo en verde (2026-10-01). Abreviaturas:
DST = `DashboardServiceTest`, DPT = `DashboardPermissionsTest`, PT = `PeriodTest`,
DSAIT = `DashboardStatisticsAdapterIT` (base propia, cifras exactas), DCIT =
`DashboardControllerIT`, OAIT = `OpenApiIT`.

| REQ | Evidencia |
|---|---|
| 1 | DCIT: `200` para los cuatro roles con las ocho claves, los tres mapas y `closedInPeriod` completos |
| 2, 3, 4 | DST (ceros en todas las claves y orden del enum); DSAIT (conteos por combinación de estado, prioridad y tipo) |
| 5 | DST (`total` y `open` sumados de las filas); DCIT (cada eje suma `total`; `open` = `pending` + `in-progress`) |
| 6 | DCIT: cada estado y cada prioridad igual al `totalItems` de `GET /work-orders?status=` y `?priority=`; DSAIT (una sola consulta de conteos) |
| 7 | DSAIT: cierres el día `from` a las `00:00:00Z` y el día `to` a las `23:59:59.999Z` entran, un milisegundo antes y el día siguiente no; `completed` y `cancelled` por separado; PT; DCIT (cerrar sube `closedInPeriod`; una orden de hoy no cuenta en un período pasado) |
| 8 | DST (reloj fijo: `to` = hoy, `from` = hoy − 29); DCIT |
| 9 | DST (solo `from`, solo `to`); DCIT (`to=2026-03-31` → `from=2026-03-02`) |
| 10 | DST (diez formatos inválidos en cada parámetro, los dos juntos, `from` > `to`, `from` futuro, un solo día válido); DCIT (`400` con `details`) |
| 11 | DST (redondeo `HALF_UP` a un decimal); DSAIT (promedio de 75 con 60 y 90 min; una `cancelled` y una `completed` fuera del período no entran; con el seed y un período amplio, no negativo) |
| 12 | DSAIT y DST: sin `completed` en el período → `null` |
| 13 | DSAIT (base vacía) y DST: todo en `0` y promedio `null` |
| 14 | DCIT: `take`, `close` y `release` por la API y el resumen cambia en la consulta siguiente |
| 15 | DSAIT (solo `in-progress`, por dueño); DCIT (la forma `{takenById, takenByName, inProgress}` y el técnico que tomó aparece con +2) |
| 16 | DST (cantidad descendente, nombre sin distinguir mayúsculas, `takenById`); DCIT (cantidades en orden descendente); DSAIT (nombre de la toma más reciente) |
| 17 | DSAIT (base vacía) y DST: lista vacía |
| 18 | DPT; DCIT: los cuatro roles `200`; sin token `401` (también con parámetros inválidos: `401` antes que `400`) |
| 19 | DPT; DST (sin consultar la base); DCIT: `produccion` y `tecnico` → `403`, `admin` y `teamleader` → `200` |
| 20 | OAIT: `/dashboard/summary` (parámetros `from` y `to`, `bearerAuth`, `200`, `400`, `401`) y `/dashboard/workload` (`bearerAuth`, `200`, `401`, `403`) |

Desvíos respecto del diseño:

- `AbstractPostgresIT.POSTGRES` pasó de paquete-privado a `public`, para que
  `DashboardStatisticsAdapterIT` (en `dashboard.persistence`) cree su base propia; los otros
  tests de ese tipo viven en el paquete raíz.
- `DashboardStatisticsAdapterIT` arma el adaptador a mano, así que la transacción
  `REPEATABLE READ` no se ejercita ahí: el contexto completo la aplica en `DashboardControllerIT`,
  pero ningún test observa el aislamiento en sí (el diseño no pedía una prueba de eso).
- Las tareas 2 y 3 se implementaron en el mismo `DashboardService` y las 4 y 5 en el mismo
  adaptador; se verificaron con `DashboardServiceTest` y `DashboardStatisticsAdapterIT`.
