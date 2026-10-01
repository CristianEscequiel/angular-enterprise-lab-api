# Tareas: Spec 03 — Órdenes de trabajo: alta, consulta, edición, baja y listado

Entorno: compilar requiere JDK 21 (`JAVA_HOME`) y Docker para las IT. Toda tarea
deja `./gradlew test` en verde. Las IT comparten un único Postgres (ver
`design.md` §7): cada test crea sus propias órdenes, máquinas y partes con
valores únicos (título con prefijo propio, p. ej. `WOCIT-`), los limpia al
terminar y no afirma sobre totales sino sobre lo que contiene. **Las 32 órdenes
del seed solo se leen.** Las migraciones de esta spec son `V7` y `V7_1`.

- [x] 1. `NumericId` compartido en `shared/domain`
  - Detalle: `shared/domain/NumericId` (pública) con `parse(String)` que devuelve
    `OptionalLong` y acepta solo de 1 a 18 dígitos ASCII. `MachineService` y
    `PartService` la usan en lugar de `machines/domain/MachineIds`, que se elimina
    (design.md §2.3). No cambia ningún comportamiento de la spec 02.
  - Depende de: —
  - Verificación: `NumericIdTest` — `"1"`, `"0"`, `"007"`, 18 dígitos válidos;
    `null`, `""`, `"abc"`, `"-1"`, `"1.5"`, `"0x1"`, dígitos no ASCII y 19
    dígitos inválidos. `MachineServiceTest`, `PartServiceTest`,
    `MachineControllerIT` y `PartControllerIT` siguen en verde sin cambios.
  - _Requisitos: habilita REQ-14, REQ-37, REQ-40_

- [x] 2. Paginación compartida y reloj
  - Detalle: en `shared/domain`, `PageQuery(int page, int size)` y
    `PageResult<T>(List<T> items, int page, int size, long totalItems)` con
    `totalPages()` = `ceil(totalItems / size)` (0 si no hay ítems); en
    `shared/web`, `PageResponse<T>(data, page, size, totalItems, totalPages)` con
    `from(PageResult<T>, Function<T, R>)`; en `shared/config`, `ClockConfig` con
    un bean `Clock` (`Clock.systemUTC()`). Sin ningún cambio en los módulos
    existentes.
  - Depende de: —
  - Verificación: `PageResultTest` — `totalPages` para 0, 1, 10, 11 y 32 ítems con
    `size` 10 y con `size` 1, y con `size` 100; `PageResponseTest` — mapea los
    ítems y copia los totales; `ClockConfigIT` (o el contexto de `ApiApplicationIT`)
    — el bean `Clock` existe.
  - _Requisitos: REQ-1, REQ-2, REQ-5 (forma de la respuesta)_

- [x] 3. Migración `V7` y `WorkOrdersMigrationIT`
  - Detalle: `db/migration/V7__work_orders_schema.sql` con `work_orders` (design.md
    §2.1): columnas planas de la orden, `machine_id` y `part_id` sin FK,
    `breadcrumb` `TEXT`, `taken_by_id` y `closing_author_id` con FK a `users`, y
    los tres `CHECK` de `type`, `priority` y `status`. Se revisa que `MigrationIT`
    no afirme sobre la lista de tablas.
  - Depende de: —
  - Verificación: `WorkOrdersMigrationIT` — columnas esperadas; cada `CHECK`
    rechaza un valor inválido de `type`, `priority` y `status`; `machine_id` y
    `part_id` aceptan ids que no existen en `machines` ni `parts` (sin FK);
    `taken_by_id` inexistente viola la FK; `machine_comment` toma `''` por
    defecto. `MigrationIT` sigue en verde.
  - _Requisitos: REQ-43_

- [x] 4. Seed de dev `V7_1`, `SeedWorkOrdersIT` y `WorkOrdersUpgradeIT`
  - Detalle: un script de una sola vez, fuera del repo (en el directorio temporal
    de la sesión), lee `work-orders` de `db.json` del frontend
    (`angular-enterprise-lab/src/app/features/work-orders/data-access/db.json`) y
    emite `db/seed/V7_1__seed_work_orders.sql` (design.md §2.2): ids `1` a `29`
    tal cual y las tres alfanuméricas como `30`, `31` y `32`; `createdAt` sin zona
    como UTC; dueños y autores `'2'` y `'5'` resueltos por subselect a los
    usuarios `tecnico` y `electricista`; comillas duplicadas; `setval` al mayor id;
    y un `SELECT` final que hace fallar la migración si una orden no `pending` quedó
    sin dueño. Se revisa el SQL generado antes de dejarlo en el repo.
  - Depende de: 3
  - Verificación: `SeedWorkOrdersIT` (perfil `dev`) — 32 órdenes con 12 `pending`,
    9 `in-progress`, 9 `completed` y 2 `cancelled`; ids `1` a `32` (las tres
    renumeradas son las últimas y `pending`); `createdAt` en UTC; `takenBy` y
    `closingNote` apuntan a `tecnico` o `electricista` con los nombres de
    `db.json`; `pending` sin dueño y cerradas con nota; cada `breadcrumb` igual al
    que arma el árbol de `machines` y `parts` del seed. `WorkOrdersUpgradeIT`
    (base propia, sin contexto de Spring) — el próximo id es `33` y sin el seed la
    tabla queda vacía.
  - _Requisitos: REQ-44_

- [x] 5. Tipos de dominio y permisos
  - Detalle: en `workorders/domain`, `WorkOrderType`, `Priority` y
    `WorkOrderStatus` (con `toValue`/`fromValue`, coincidencia exacta, como
    `TeamType`), los records `WorkOrder`, `MachineRef`, `TakenBy` y `ClosingNote`,
    los comandos `CreateWorkOrderCommand`, `UpdateWorkOrderCommand`,
    `WorkOrderQuery` y `WorkOrderFilter`, y `WorkOrderPermissions`
    (`requireView`, `requireCreateAny`, `requireCreate(rol, tipo)`, `requireEdit`,
    `requireDelete`).
  - Depende de: —
  - Verificación: `WorkOrderTypeTest`, `PriorityTest` y `WorkOrderStatusTest` —
    ida y vuelta de cada valor y rechazo de mayúsculas, espacios, `null` y
    desconocidos. `WorkOrderPermissionsTest` — matriz de los cuatro roles para cada
    método, y la matriz **4 roles × 3 tipos** de `requireCreate` (team leader →
    `preventivo` y `correctivo`; producción → `pronto-intervencion`; administrador y
    técnico, ninguno); un rol `null` da `403`.
  - _Requisitos: REQ-16, REQ-17, REQ-38, REQ-41, REQ-42_

- [x] 6. Puertos, entity y adaptadores de persistencia
  - Detalle: `WorkOrderRepository`, `MachineDirectory` y `PartLocation` en
    `domain`; `WorkOrderEntity` (columnas planas, `updateDetails` como único
    mutador), `WorkOrderJpaRepository` (con `JpaSpecificationExecutor`),
    `WorkOrderSpecifications` (título con `escape`, estado y prioridad),
    `WorkOrderMapper`, `WorkOrderRepositoryAdapter` (`search` con
    `PageRequest.of(page - 1, size, Sort.by("id"))`, `save` que en la edición
    carga la entity y llama `updateDetails`, `NotFoundException` si ya no existe) y
    `MachineDirectoryAdapter`, que se apoya en `MachineRepository` y
    `PartRepository` de `machines/domain` y arma la cadena subiendo por `parentId`
    con un tope de iteraciones (design.md §3).
  - Depende de: 3, 5
  - Verificación: `WorkOrderRepositoryAdapterIT` — guardar y leer una orden con y
    sin parte, `takenBy` y `closingNote` nulos y cargados; `search` por página y
    tamaño con `totalItems` y `totalPages`; título sin distinguir mayúsculas, con
    espacios en los bordes, `%`, `_` y `\` literales, vacío ignorado; estado y
    prioridad; los tres combinados; página fuera de rango con `content` vacío y
    totales reales; orden por `id` numérico; `save` de una orden que otro borró →
    `NotFoundException`. `MachineDirectoryAdapterIT` — nombre de una máquina, máquina
    inexistente, cadena de una parte de nivel 1, 3 y 5 con los nombres en orden,
    parte inexistente.
  - _Requisitos: REQ-2, REQ-4 a REQ-12, REQ-23, REQ-24_

- [x] 7. `WorkOrderService`: listado y consulta
  - Detalle: `list(actor, query)` y `get(actor, id)` (design.md §4.2 y §4.3):
    `requireView`; validación de `page` (≥ 1, por defecto 1), `size` (1 a 100, por
    defecto 10), `status` y `priority` con todos los errores juntos; `title`
    recortado y vacío = sin filtro; id con `NumericId`.
  - Depende de: 1, 2, 5, 6
  - Verificación: `WorkOrderServiceTest` (Mockito sobre los puertos) — valores por
    defecto; `page=0`, `size=0`, `size=101`, `page=abc`, `size=1.5`, un número fuera
    de `int` y `status`/`priority` inválidos, cada uno con su clave y varios juntos;
    `size=100` y `page=1` válidos; título en blanco no filtra; `get` de un id
    inexistente y de uno no numérico → `NotFoundException`; los cuatro roles leen.
  - _Requisitos: REQ-1, REQ-3, REQ-5, REQ-8, REQ-12, REQ-13, REQ-14, REQ-42_

- [x] 8. `WorkOrderService`: alta
  - Detalle: `create(actor, cmd)` con el orden de design.md §4.1 y §4.2:
    `requireCreateAny` → si `type` es válido, `requireCreate(actor, tipo)` →
    validación de todo el comando (título 3–150, descripción 10–2000, tipo,
    prioridad, `machineRef.machineId` obligatorio, comentario ≤ 200, recortes) →
    referencias (máquina primero, después parte; `MACHINE_NOT_FOUND`,
    `PART_NOT_FOUND`, `PART_OTHER_MACHINE`) → orden `pending` con `createdAt` del
    `Clock` truncado a milisegundos, `breadcrumb` y comentario → `save`.
  - Depende de: 5, 6
  - Verificación: `WorkOrderServiceTest` con un `Clock` fijo — alta válida de cada
    tipo con su rol (`pending`, `takenBy` nulo, sin nota, `createdAt` del reloj);
    **matriz 4 roles × 3 tipos** con el `403` y nada guardado; límites de título y
    descripción (justo dentro y justo fuera, tras recortar); ausentes; tipo y
    prioridad inválidos; todos los errores juntos; sin `machineRef` y sin
    `machineId` → `VALIDATION_ERROR`; máquina inexistente, vacía y no numérica →
    `MACHINE_NOT_FOUND`; parte inexistente, vacía y no numérica →
    `PART_NOT_FOUND`; parte de otra máquina → `PART_OTHER_MACHINE`; con máquina y
    parte malas gana `MACHINE_NOT_FOUND`; `breadcrumb` de máquina sola y de una
    parte con ` > `; comentario recortado, ausente → `""`, 200 pasa y 201 falla y
    sin entrar al `breadcrumb`; `administrador` con cuerpo vacío → `403`; `type`
    inválido → `400` y no `403`; team leader con `pronto-intervencion` y otros
    campos inválidos → `403`.
  - _Requisitos: REQ-15 a REQ-28, REQ-31, REQ-46_

- [x] 9. `WorkOrderService`: edición y baja
  - Detalle: `update(actor, id, cmd)` — `requireEdit` → validar `title`,
    `description` y `priority` → `findById` (`404`) → intentos de cambio de `type`,
    `machineRef.machineId`, `machineRef.partId` (enviado aunque sea `null`) y
    `machineRef.comment` como `ValidationFailedException` con sus claves → `save`
    de los tres campos. `delete(actor, id)` — `requireDelete` → `findById` →
    `deleteById`.
  - Depende de: 5, 6
  - Verificación: `WorkOrderServiceTest` — edición válida y recorte; título y
    descripción fuera de rango y prioridad inválida; `type`, `machineId`, `partId` y
    `comment` distintos → `400` con la clave correcta y nada guardado; iguales o
    ausentes se aceptan; `partId: null` enviado a una orden con parte → `400`; en
    una orden sin parte un `partId` `null` enviado es aceptado; el estado, el
    dueño y la nota del `save` no cambian en `in-progress`, `completed` y
    `cancelled`; `PUT` inválido sobre un id inexistente → `400` y `PUT` que cambia
    el tipo sobre un id inexistente → `404`; `delete` de una orden en cada estado,
    de un id inexistente y de uno no numérico; `403` por cada rol sin permiso
    (4 roles × `update` y × `delete`).
  - _Requisitos: REQ-32 a REQ-41, REQ-46_

- [x] 10. `WorkOrderController`: listado y consulta, y DTOs de respuesta
  - Detalle: en `workorders/web`, `WorkOrderResponse`, `MachineRefResponse`,
    `TakenByResponse` y `ClosingNoteResponse` (ids como string, `createdAt` como
    `Instant`, `takenBy` y `partId` como `null` explícito, `closingNote` ausente si
    no hay), y `WorkOrderController` (`/work-orders`) con `GET` de listado
    (`page`, `size`, `title`, `status` y `priority` como `String`, respuesta
    `PageResponse`) y `GET /{id}`. Anotaciones OpenAPI (`@Tag`, `@Operation`,
    `@Parameter`, `@ApiResponse`, `bearerAuth`).
  - Depende de: 7
  - Verificación: `WorkOrderControllerIT` — listado sin parámetros (forma
    `{data, page, size, totalItems, totalPages}`, `size` 10, `page` 1, por `id`);
    `page` y `size`; `page` muy alta → `200` con `data` vacío y totales reales; un
    filtro sin coincidencias → `totalItems` 0 y `totalPages` 0; `title` sin
    distinguir mayúsculas y con `%` literal; `status` y `priority` y los tres
    combinados; parámetros inválidos → `400` con el detalle; consulta de la orden
    `3` del seed con `machineRef`, `takenBy` y `closingNote`; una `pending` del
    seed con `takenBy` `null` y sin `closingNote`; id inexistente y no numérico →
    `404`; los cuatro roles leen; sin token → `401`.
  - _Requisitos: REQ-1 a REQ-14, REQ-42_

- [x] 11. `POST /work-orders`
  - Detalle: `WorkOrderCreateRequest` y `MachineRefRequest` (records de `String`;
    las propiedades desconocidas, como `id` o `status`, las descarta Jackson) y el
    endpoint `POST` con `201` y `Location: /work-orders/{id}`. Anotaciones
    OpenAPI, con el `201` declarado.
  - Depende de: 8, 10
  - Verificación: `WorkOrderControllerIT` — `201` con `id`, `Location`, `pending`,
    `createdAt` del servidor, `takenBy` `null` y sin `closingNote`; cada tipo con
    su rol; `administrador` y `tecnico` → `403`, team leader con
    `pronto-intervencion` y producción con `preventivo`/`correctivo` → `403`, y no
    se crea nada; título, descripción, tipo, prioridad y comentario inválidos →
    `400`; sin `machineRef` → `400`; máquina inexistente → `MACHINE_NOT_FOUND`;
    orden sobre la máquina (`breadcrumb` = nombre) y sobre una parte de nivel 1, 3
    y 5 (cadena completa); parte inexistente y de otra máquina; comentario
    recortado y fuera del `breadcrumb`; `POST` con `id`, `status: completed`,
    `createdAt`, `takenBy`, `closingNote` y `machineRef.breadcrumb` → se crea
    `pending`, sin dueño ni nota, con el `createdAt` del servidor y el
    `breadcrumb` calculado; renombrar la máquina y las partes de la cadena y
    eliminar la parte y la máquina por la API de la spec 02 no cambia el
    `machineRef` de la orden; orden de errores de REQ-46 (`403` sobre `400`,
    `type` inválido → `400`, formato sobre referencia).
  - _Requisitos: REQ-15 a REQ-31, REQ-46_

- [x] 12. `PUT` y `DELETE /work-orders/{id}`
  - Detalle: `WorkOrderUpdateRequest` (clase con `MachineRefPatch` y los setters
    que marcan si `partId` vino) y los endpoints `PUT` (`200` con la orden
    completa) y `DELETE` (`204`). Anotaciones OpenAPI.
  - Depende de: 9, 10
  - Verificación: `WorkOrderControllerIT` — `PUT` de título, descripción y
    prioridad con la orden completa en el cuerpo (como lo manda el frontend) y con
    solo los tres campos; recorte; el estado, el dueño y la nota se conservan en
    órdenes propias en `pending`, `in-progress`, `completed` y `cancelled` (estado,
    dueño y nota forzados por SQL); `type`, `machineRef.machineId`,
    `machineRef.partId` (también `null` a una orden con parte) y
    `machineRef.comment` distintos → `400` y orden intacta; `status`, `takenBy`,
    `closingNote`, `createdAt`, `id` y `breadcrumb` distintos se ignoran; `PUT` y
    `DELETE` de un id inexistente o no numérico → `404` sin altas; `produccion` y
    `tecnico` → `403` en `PUT`; `teamleader`, `produccion` y `tecnico` → `403` en
    `DELETE` y la orden sigue; `administrador` elimina en cada estado con `204`;
    orden de errores de REQ-46 (`PUT` inválido a un id inexistente → `400`;
    cambio de tipo a un id inexistente → `404`; `403` gana a un id mal formado).
  - _Requisitos: REQ-32 a REQ-41, REQ-46_

- [x] 13. Documentación OpenAPI y `OpenApiIT`
  - Detalle: se amplía `OpenApiIT` con las rutas de `workorders`; se revisa que
    los parámetros del listado y los códigos de cada operación salgan de las
    anotaciones.
  - Depende de: 11, 12
  - Verificación: `OpenApiIT` — `/v3/api-docs` contiene `/work-orders` (`get`,
    `post`) y `/work-orders/{id}` (`get`, `put`, `delete`) con `bearerAuth`,
    `401` y `403`; el `get` de listado declara `page`, `size`, `title`, `status` y
    `priority`; el `post` declara `201`, `400`; el `put` y el `delete` declaran
    `404`; el esquema `WorkOrderResponse` tiene los campos de la orden.
  - _Requisitos: REQ-45_

- [x] 14. Cierre: README, estado actual y recorrido de requisitos
  - Detalle: README con los endpoints nuevos, el seed de órdenes (con la nota de
    las tres renumeradas) y ejemplos de listado con filtros; actualizar "Estado
    actual" de `CLAUDE.md` (la spec 04 numera desde `V8` si necesita migrar) y el
    estado de la spec 03 en `ROADMAP.md`; agregar al final de este archivo el
    recorrido de REQ-1 a REQ-46 con su evidencia, como en las specs 01 y 02.
  - Depende de: 1 a 13
  - Verificación: `./gradlew test` completo en verde; cada REQ con un test que lo
    cubra en el recorrido.
  - _Requisitos: REQ-1 a REQ-46_

## Cierre: recorrido de REQ-1 a REQ-46 (2026-09-30)

Evidencia: `./gradlew test` en verde (908 tests, sin fallas ni saltos) con Postgres
real en Testcontainers. No se hizo verificación manual contra la API levantada:
toda la evidencia es automatizada.
Siglas: **WCIT** = `WorkOrderControllerIT`, **WST** = `WorkOrderServiceTest`,
**WPT** = `WorkOrderPermissionsTest`, **WRA** = `WorkOrderRepositoryAdapterIT`,
**MDA** = `MachineDirectoryAdapterIT`, **WMIT** = `WorkOrdersMigrationIT`,
**SWIT** = `SeedWorkOrdersIT`, **WUIT** = `WorkOrdersUpgradeIT`,
**OAIT** = `OpenApiIT`.

| REQ | Evidencia |
|---|---|
| 1 | WCIT: sin parámetros → forma `{data, page, size, totalItems, totalPages}`, `page` 1, `size` 10, ids `1` a `10` en orden; WST: valores por defecto |
| 2 | WCIT: `page=2&size=5` → ids `6` a `10` y `totalPages` acorde; WRA: páginas de 2 con `totalItems` y `totalPages` reales; `PageResultTest` |
| 3 | WST y WCIT: `page=0`, `-1`, `abc`, `1.5`, `99999999999`; `size=0`, `101`, `abc`, `1.5` → `400 VALIDATION_ERROR` con el parámetro en `details`, y todos juntos |
| 4 | WCIT y WRA: `page=9999` → `200` con `data` vacío y totales reales |
| 5 | WCIT y WRA: un filtro sin coincidencias → `data` vacío, `totalItems` 0 y `totalPages` 0; `PageResultTest` |
| 6 | WRA y WCIT: sin distinguir mayúsculas y con espacios en los bordes |
| 7 | WRA y WCIT: `%`, `_` y `\` se buscan literalmente |
| 8 | WST, WRA y WCIT: título vacío o en blanco no filtra |
| 9 | WRA y WCIT: `status=cancelled` → solo esas órdenes y los totales de la base |
| 10 | WRA y WCIT: `priority=high` → solo esas órdenes y los totales de la base |
| 11 | WRA y WCIT: título, estado y prioridad combinados con `AND`, con sus totales |
| 12 | WST y WCIT: `status=open`, `Pending`; `priority=urgent`, `HIGH` → `400` y no lista vacía |
| 13 | WCIT: orden `3` del seed con `machineRef`, `takenBy` y `closingNote`; una `pending` con `takenBy` `null` y sin `closingNote` |
| 14 | WCIT y WST: id inexistente, no numérico y negativo → `404 NOT_FOUND` con `ApiError` |
| 15 | WCIT: `201` con `id`, `Location`, `pending`, `createdAt` del servidor (ms, UTC), `takenBy` `null` y sin `closingNote`; WST con un `Clock` fijo |
| 16 | WCIT y WST: team leader crea `preventivo` y `correctivo`; producción crea `pronto-intervencion`; WPT |
| 17 | WPT y WST: matriz **4 roles × 3 tipos**; WCIT: nueve combinaciones prohibidas → `403` y no se crea nada |
| 18 | WST y WCIT: título 2/3/150/151 y descripción 9/10/2000/2001 tras recortar, ausentes y en blanco; `400` con el campo; también en el `PUT` |
| 19 | WST y WCIT: tipo y prioridad ausentes o inválidos → `400` en el campo; `WorkOrderEnumsTest` |
| 20 | WST y WCIT: título y descripción guardados sin espacios en los bordes, en el alta y en la edición |
| 21 | WST y WCIT: sin `machineRef` → `machineRef`; sin `machineId` → `machineRef.machineId` |
| 22 | WST y WCIT: máquina inexistente, vacía y no numérica → `400 MACHINE_NOT_FOUND` y nada se crea |
| 23 | WST, MDA y WCIT: `partId` ausente o nulo → `partId` `null` y `breadcrumb` = nombre de la máquina |
| 24 | MDA, WST y WCIT: parte de nivel 1, 3, 4 y 5 → `breadcrumb` con toda la cadena en orden y separada por ` > `; árbol de cinco niveles creado por la API |
| 25 | WST y WCIT: `partId` inexistente, vacío y no numérico → `400 PART_NOT_FOUND` |
| 26 | WST y WCIT: parte de otra máquina → `400 PART_OTHER_MACHINE`; con máquina y parte malas gana `MACHINE_NOT_FOUND` |
| 27 | WST y WCIT: comentario recortado, ausente → `""`, y fuera del `breadcrumb` |
| 28 | WST y WCIT: 200 caracteres pasa y 201 → `400` en `machineRef.comment` |
| 29 | WCIT: se renombran la máquina y dos partes por la API de la spec 02 y la orden conserva su `breadcrumb` |
| 30 | WCIT: se eliminan la parte y la máquina por la API de la spec 02 y la orden conserva su `machineRef` completo; WMIT: sin FK hacia `machines` ni `parts` |
| 31 | WCIT: `POST` con `id`, `status: completed`, `createdAt`, `takenBy`, `closingNote` y `machineRef.breadcrumb` → se crea `pending`, sin dueño ni nota, con el `createdAt` del servidor y el `breadcrumb` calculado |
| 32 | WCIT: `PUT` de los tres campos → `200` con la orden completa; también con la orden completa en el cuerpo, como lo manda el frontend |
| 33 | WST y WCIT: `type` distinto → `400` en `type` y orden intacta; igual, ausente o `null` se acepta |
| 34 | WST y WCIT: `machineId`, `partId` (también `null` a una orden con parte) y `comment` distintos → `400` con la clave y orden intacta; iguales o ausentes se aceptan; `partId: null` a una orden sin parte no es un cambio |
| 35 | WCIT: `PUT` con `status`, `takenBy`, `closingNote`, `createdAt`, `id` y `machineRef.breadcrumb` distintos → se ignoran |
| 36 | WST y WCIT: `PUT` en `pending`, `in-progress`, `completed` y `cancelled` (forzados por SQL) conserva estado, dueño y nota de cierre; WRA |
| 37 | WST y WCIT: `PUT` de id inexistente o no numérico → `404` y no crea nada |
| 38 | WPT y WST (4 roles × `PUT`); WCIT: `produccion` y `tecnico` → `403` y la orden queda intacta |
| 39 | WCIT: `DELETE` de una orden en cada estado → `204` y luego `404`; WST; WRA |
| 40 | WCIT y WST: `DELETE` de id inexistente o no numérico → `404` |
| 41 | WPT y WST (4 roles × `DELETE`); WCIT: `teamleader`, `produccion` y `tecnico` → `403` y la orden sigue |
| 42 | WPT y WST; WCIT: los cuatro roles reciben `200` en el listado y en la consulta; sin token → `401` |
| 43 | WMIT: columnas, los tres `CHECK` rechazan valores inválidos, `machine_id` y `part_id` aceptan ids inexistentes, FK de dueño y autor a `users` |
| 44 | SWIT: 32 órdenes con 12/9/9/2 por estado, ids `1` a `32` (`30` a `32` renumeradas), `createdAt` en UTC, dueños y autores en `tecnico` y `electricista` con los nombres de `db.json`, y cada `breadcrumb` igual al que arma el árbol del seed; WUIT: la próxima orden es la `33` y sin el seed la tabla queda vacía |
| 45 | OAIT: `/work-orders` (`get`, `post`) y `/work-orders/{id}` (`get`, `put`, `delete`) con `bearerAuth`, `401`, `403`, los cinco parámetros del listado y los códigos propios de cada operación |
| 46 | WCIT y WST: `403` sobre `400` (administrador con cuerpo vacío; team leader con `pronto-intervencion` y campos inválidos; id mal formado); `type` inválido → `400` y no `403`; `400` de formato sobre `400` de referencia; `PUT` inválido a un id inexistente → `400`; cambio de tipo a un id inexistente → `404` |

Desvíos respecto del diseño:

- Las tareas 10, 11 y 12 se implementaron juntas, porque comparten el mismo
  controller y los DTOs; se verificaron con un solo `WorkOrderControllerIT`.
- `page` o `size` **vacíos** (`?page=`) son un error de formato (`400`): el diseño
  decía "ausente → valor por defecto" y un vacío no es ausente. `status` y
  `priority` vacíos sí se ignoran, como decía el diseño. `+1` tampoco es un entero
  válido (solo `-?[0-9]+`).
- `PageResult` suma `map(...)`, que el diseño no listaba.
- Se agregaron tests que el diseño no pedía: `MachineDirectoryAdapterTest` (el tope
  de iteraciones ante un ciclo, que la FK compuesta permite solo con una parte que
  se apunta a sí misma) y `WorkOrderEnumsTest`.
- Los `200` de listado, consulta y edición se declaran en `@ApiResponse` para que
  aparezcan en OpenAPI (las respuestas anotadas reemplazan la de éxito por defecto).
- `WorkOrderService.create` recibe `machineRefSent` antes de `machineId` en el
  comando (el diseño lo listaba al final); no cambia el comportamiento.
