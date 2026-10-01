# Tareas: Spec 02 — Máquinas y árbol de partes

Entorno: compilar requiere JDK 21 (`JAVA_HOME`) y Docker para las IT. Toda tarea
deja `./gradlew test` en verde. Las IT comparten un único Postgres (ver
`design.md` §7): cada test usa códigos y partes propios y los limpia al terminar.
Las migraciones de esta spec son `V6` y `V6_1`.

- [x] 1. Mover `ConstraintViolations` a `shared/persistence`
  - Detalle: pasa de `maintenance/persistence` (paquete-privada) a
    `shared/persistence` como clase pública; `TechnicianRepositoryAdapter` y
    `TeamRepositoryAdapter` solo cambian el `import` (design.md §3, §8 punto 5).
  - Depende de: —
  - Verificación: `./gradlew test` sigue en verde, en particular las IT de
    `maintenance` que cubren la traducción por constraint.
  - _Requisitos: habilita REQ-7, REQ-11, REQ-14, REQ-28, REQ-33_

- [x] 2. Migración `V6` y `MachinesMigrationIT`
  - Detalle: `db/migration/V6__machines_schema.sql` con `machines` (`code`
    `UNIQUE` y `CHECK` de formato) y `parts` (FK a `machines`, `UNIQUE (id,
    machine_id)`, FK compuesta `parts_parent_same_machine_fkey`, sin cascada) e
    índices por `machine_id` y `parent_id` (design.md §2.1). Se revisa que
    `MigrationIT` no afirme sobre la lista de tablas.
  - Depende de: —
  - Verificación: `MachinesMigrationIT` — `code` único y `CHECK` de formato;
    una parte con máquina o padre inexistente viola la FK; un padre de otra
    máquina viola `parts_parent_same_machine_fkey`; borrar una máquina con
    partes o una parte con hijos viola la FK. `MigrationIT` sigue en verde.
  - _Requisitos: REQ-33_

- [x] 3. Seed de dev `V6_1` y `SeedMachinesIT`
  - Detalle: `db/seed/V6_1__seed_machines.sql` con las 3 máquinas y las 10 partes
    con los ids de `db.json` (leer nombres y forma del árbol de `db.json` del
    frontend, sin inventarlos), padres antes que hijos, y `setval` de las dos
    secuencias al mayor id (design.md §2.2). Si `db.json` no está al alcance,
    se pide al usuario antes de escribir el seed.
  - Depende de: 2
  - Verificación: `SeedMachinesIT` (perfil `dev`) — `ENV-01`, `SEL-02` y
    `ROT-03` con ids `1` a `3`; 10 partes con ids `1` a `10` y la forma del
    árbol (Envasadora de 4 niveles con una hoja hermana en el nivel 2,
    Selladora de 2, Rotuladora sin partes); el próximo alta de máquina es `4`
    y el de parte `11`. Un segundo test migra solo con `db/migration` y
    verifica `machines` y `parts` vacías.
  - _Requisitos: REQ-34_

- [x] 4. Tipos de dominio, `MachineCode` y permisos
  - Detalle: en `machines/domain`, los records `Machine` y `Part`,
    `MachineCommand`, `PartCommand` y `PartPatchCommand`, `MachineCode`
    (`normalize` con `strip` + `toUpperCase(Locale.ROOT)`, `isValid` sobre el
    valor normalizado) y `MachinesPermissions` (`requireRead` con los cuatro
    roles, `requireWrite` con administrador y team leader).
  - Depende de: —
  - Verificación: `MachineCodeTest` — `" env-01 "` → `ENV-01`; inválidos: vacío,
    21 caracteres, `-A`, `A B`, `A_1`, `ß`, `null`; 20 caracteres válido.
    `MachinesPermissionsTest` — la matriz por rol (4 roles × lectura y
    escritura) y un rol `null` da `403`.
  - _Requisitos: REQ-5, REQ-6, REQ-31, REQ-32_

- [x] 5. Puertos, entities y adaptadores de persistencia
  - Detalle: `MachineRepository` y `PartRepository` en `domain`;
    `MachineEntity`, `PartEntity` (ids sueltos, sin relaciones),
    `MachineJpaRepository` (con la proyección JPQL de `partCount`),
    `PartJpaRepository`, `MachineRepositoryAdapter`, `PartRepositoryAdapter` y
    mappers manuales. Adaptadores `@Transactional`, con `saveAndFlush` y
    traducción por constraint (design.md §3 y §6).
  - Depende de: 1, 2, 4
  - Verificación: `MachineRepositoryAdapterIT` y `PartRepositoryAdapterIT` —
    `findAll` con `partCount` en una consulta, orden por id,
    `existsByCodeAndIdNot`, conteo de hijos; dos `save` que se saltean el chequeo
    previo traducen `machines_code_key` a `DUPLICATE_MACHINE_CODE`; borrar con
    dependientes agregados a último momento traduce a `MACHINE_HAS_PARTS` y
    `PART_HAS_CHILDREN`; insertar con padre o máquina borrados justo antes
    traduce a `PARENT_PART_NOT_FOUND` y `NotFoundException`.
  - _Requisitos: REQ-7, REQ-11, REQ-14, REQ-28, REQ-33_

- [x] 6. `MachineService`
  - Detalle: `list`, `get`, `create`, `update` y `delete` con el orden de
    evaluación de design.md §4.1 y §4.2: rol → validar `code` y `name` (todos
    los errores juntos; `name` máximo 100) → existencia → duplicado →
    dependientes. Ids no numéricos de hasta 18 dígitos son `404`.
  - Depende de: 4, 5
  - Verificación: `MachineServiceTest` (Mockito sobre los puertos) — alta y
    edición válidas con `code` normalizado; `code` inválido y `name` vacío, solo
    espacios, de 100 (pasa) y de 101 (falla) caracteres; duplicado al crear y al
    editar; mismo código en otra capitalización se acepta; `404` en `get`,
    `update` y `delete`; baja con partes `409 MACHINE_HAS_PARTS` con la cantidad
    en el mensaje; `403` por cada rol sin permiso y operación (4 roles × 5);
    `PUT` inválido sobre id inexistente responde `400`.
  - _Requisitos: REQ-1 a REQ-15, REQ-31, REQ-32, REQ-36, REQ-37_

- [x] 7. `PartService`
  - Detalle: `listByMachine`, `create`, `rename` y `delete` (design.md §4.3):
    `name` obligatorio y máximo 100 → `404` de la máquina o la parte →
    `resolveParent` (`PARENT_PART_NOT_FOUND`, `PARENT_PART_OTHER_MACHINE`) o, en
    `rename`, el chequeo de que `machineId` y `parentId` enviados no cambian →
    `409 PART_HAS_CHILDREN` en la baja. Depende del puerto `MachineRepository`
    para la existencia de la máquina.
  - Depende de: 4, 5
  - Verificación: `PartServiceTest` — alta de primer nivel (`parentId` nulo) y de
    sub-parte; `parentId` inexistente, no numérico y `""`; padre de otra
    máquina; máquina inexistente (`404` gana al `400` de padre); `rename` solo
    cambia el nombre; `machineId` o `parentId` distintos → `400 VALIDATION_ERROR`,
    iguales o ausentes se ignoran, `parentId: null` enviado a una sub-parte →
    `400`; baja con hijos `409` con la cantidad en el mensaje; `403` por cada
    rol sin permiso y operación (4 roles × 4); orden `400 → 404 → 400 → 409`.
  - _Requisitos: REQ-16 a REQ-30, REQ-31, REQ-32, REQ-36, REQ-37_

- [x] 8. `MachineController`, DTOs y OpenAPI
  - Detalle: en `machines/web`, `MachineRequest`, `MachineResponse` (ids como
    string) y `MachineController` (`/machines`) fino: `AuthenticatedRole.from`
    + servicio + `Response`. `POST` responde `201` con `Location`. Anotaciones
    `@Tag`, `@Operation`, `@ApiResponse` y `bearerAuth`.
  - Depende de: 6
  - Verificación: `MachineControllerIT` — listado con las 3 máquinas del seed y
    su `partCount`; consulta de `1`, de un id inexistente y de uno no numérico;
    alta `201` con `id` y `partCount` 0; código con minúsculas y espacios
    normalizado; `400` de código y nombre; `409 DUPLICATE_MACHINE_CODE` (`env-01`
    contra `ENV-01`) sin máquina nueva; edición que conserva el `id` y las
    partes; `PUT` inexistente `404` sin alta; baja `204`; baja de `1`
    `409 MACHINE_HAS_PARTS` con las partes intactas; `produccion` y `tecnico`
    reciben `403` en `POST`, `PUT` y `DELETE` (y el `403` gana a un id inválido);
    los cuatro roles leen.
  - _Requisitos: REQ-1 a REQ-15, REQ-31, REQ-32, REQ-36, REQ-37_

- [x] 9. `PartController`, `PATCH` con campos enviados y CORS
  - Detalle: `PartRequest`, `PartPatchRequest` (clase con setters que marcan
    `sent`), `PartResponse` y `PartController` (`/machines/{machineId}/parts` y
    `/parts/{id}`). Verificar que CORS permita `PATCH` y sumarlo si falta
    (design.md §2.3). `Location` en el `201`, anotaciones OpenAPI.
  - Depende de: 7, 8
  - Verificación: `PartControllerIT` — listado de la Envasadora en orden de
    creación; alta de primer nivel (con `parentId` nulo y ausente) y de
    sub-parte; `404` en máquina inexistente; padre inexistente y de otra
    máquina con su `code`; `PATCH` cambia solo el nombre; `PATCH` con
    `machineId` o `parentId` distintos `400` y parte intacta; `404` en `PATCH` y
    `DELETE` de parte inexistente; baja de hoja `204`; baja de un padre
    `409 PART_HAS_CHILDREN`; árbol de 5 niveles creado por la API y
    reconstruido desde el listado; `403` a `produccion` y `tecnico` en `POST`,
    `PATCH` y `DELETE`; los cuatro roles leen; un `OPTIONS` preflight de
    `PATCH` desde el origen permitido responde OK.
  - _Requisitos: REQ-16 a REQ-32, REQ-36, REQ-37_

- [x] 10. Documentación OpenAPI y `OpenApiIT`
  - Detalle: se amplía `OpenApiIT` con las rutas de `machines`; se revisa que
    las anotaciones de los dos controllers muestren los `400/403/404/409` con
    `ApiError`.
  - Depende de: 8, 9
  - Verificación: `OpenApiIT` — `/v3/api-docs` contiene `/machines`,
    `/machines/{id}`, `/machines/{machineId}/parts` y `/parts/{id}` con sus
    métodos y `bearerAuth`.
  - _Requisitos: REQ-35_

- [x] 11. Cierre: README, estado actual y recorrido de requisitos
  - Detalle: README con los endpoints nuevos; actualizar "Estado actual" de
    `CLAUDE.md` y el estado de la spec 02 en `ROADMAP.md`; agregar al final de
    este archivo el recorrido de REQ-1 a REQ-37 con su evidencia, como en las
    specs 00 y 01.
  - Depende de: 1 a 10
  - Verificación: `./gradlew test` completo en verde; cada REQ con un test que
    lo cubra en el recorrido.
  - _Requisitos: REQ-1 a REQ-37_

## Cierre: recorrido de REQ-1 a REQ-37 (2026-09-30)

Evidencia: `./gradlew test` en verde (589 tests, sin fallas ni saltos) con
Postgres real en Testcontainers. No se hizo verificación manual contra la API
levantada; toda la evidencia es automatizada.
Siglas: **MCIT** = `MachineControllerIT`, **PCIT** = `PartControllerIT`,
**MST** = `MachineServiceTest`, **PST** = `PartServiceTest`,
**MRA** = `MachineRepositoryAdapterIT`, **PRA** = `PartRepositoryAdapterIT`,
**MMIT** = `MachinesMigrationIT`, **MUIT** = `MachinesUpgradeIT`,
**SMIT** = `SeedMachinesIT`, **MCT** = `MachineCodeTest`,
**MPT** = `MachinesPermissionsTest`.

| REQ | Evidencia |
|---|---|
| 1 | MCIT: listado con las 3 máquinas del seed, los cuatro campos y `partCount` 7, 3 y 0; MRA: `partCount` en una consulta, por id |
| 2 | MCIT: `GET /machines/1` con los mismos cuatro campos del listado |
| 3 | MCIT: id inexistente y no numérico → `404 NOT_FOUND` con `ApiError`; MST |
| 4 | MCIT: alta → `201` con `id`, `Location` y `partCount` 0; MST |
| 5 | MCT; MCIT: `"  mcit-2  "` se guarda como `MCIT-2` |
| 6 | MCT (vacío, 21 caracteres, `-A`, `A B`, `A_1`, `ß`); MCIT: `400` con detalle `code`; MMIT: el `CHECK` de la base |
| 7 | MCIT: `mcit-dup` contra `MCIT-DUP` → `409 DUPLICATE_MACHINE_CODE` sin fila nueva; MRA: dos `save` que saltean el servicio traducen `machines_code_key` |
| 8 | MST y MCIT: nombre ausente, vacío o en blanco → `400`; se guarda recortado |
| 9 | MCIT: `PUT` cambia código y nombre y conserva el `id` y las partes |
| 10 | MCIT y MST: mismo código con otra capitalización o solo otro nombre; MRA: `existsByCodeAndIdNot` excluye a la propia máquina |
| 11 | MCIT: `PUT` con el código de otra → `409` y la máquina no cambia; MRA |
| 12 | MCIT: `PUT` sobre id inexistente → `404` y no crea nada |
| 13 | MCIT: baja sin partes → `204` y luego `404` |
| 14 | MCIT: baja de `1` → `409 MACHINE_HAS_PARTS` con "7 partes" en el `message` y las partes intactas; MRA y MMIT: la FK también se traduce |
| 15 | MCIT: baja de id inexistente → `404` |
| 16 | PCIT: partes de la Envasadora como lista plana por id con los cuatro campos y los `parentId` del seed; PRA |
| 17 | PCIT: máquina inexistente o no numérica → `404` |
| 18 | PCIT: alta de primer nivel con `parentId` ausente y con `null` → `201`, `parentId` nulo y `Location` |
| 19 | PCIT: sub-parte de una parte de la misma máquina → `201` |
| 20 | PCIT: alta en máquina inexistente → `404` y sin filas nuevas |
| 21 | PCIT y PST: `parentId` inexistente, no numérico o vacío → `400 PARENT_PART_NOT_FOUND`; PRA: padre borrado a último momento |
| 22 | PCIT y PST: padre de otra máquina → `400 PARENT_PART_OTHER_MACHINE`; MMIT: la FK compuesta lo rechaza en la base |
| 23 | PCIT y PST: nombre ausente, vacío o en blanco → `400` en alta y `PATCH`; se guarda recortado |
| 24 | PCIT: `PATCH` cambia solo el nombre y conserva máquina, padre y posición; PRA |
| 25 | PCIT y PST: `machineId` o `parentId` distintos → `400 VALIDATION_ERROR` con el detalle del campo y la parte intacta; iguales o ausentes se ignoran; `parentId: null` enviado a una sub-parte → `400` |
| 26 | PCIT: `PATCH` de id inexistente o no numérico → `404` |
| 27 | PCIT: baja de una hoja → `204` |
| 28 | PCIT: baja de un padre → `409 PART_HAS_CHILDREN` con "2 sub-partes" y el subárbol intacto; PRA y MMIT |
| 29 | PCIT: baja de id inexistente o no numérico → `404` |
| 30 | PCIT: árbol de cinco niveles con una hoja hermana creado por la API y reconstruido desde el listado; baja del subárbol de las hojas hacia arriba |
| 31 | MPT; MST y PST (4 roles × cada operación de escritura); MCIT y PCIT: `produccion` y `tecnico` reciben `403` en `POST`, `PUT`, `PATCH` y `DELETE` y nada cambia; administrador y team leader escriben |
| 32 | MPT; MST y PST; MCIT y PCIT: los cuatro roles reciben `200` en las tres lecturas |
| 33 | MMIT: `code` único y con formato, FK de máquina y de padre, padre de otra máquina, sin cascada en ambos borrados |
| 34 | SMIT: 3 máquinas y 10 partes con los ids y la forma de `db.json`; MUIT: los próximos ids son `4` y `11`, y sin el seed las tablas quedan vacías |
| 35 | `OpenApiIT`: las cuatro rutas con sus métodos, `bearerAuth`, `401`/`403` y los códigos propios de cada operación |
| 36 | MCIT y PCIT: `403` sobre `400` y sobre id inválido; `400` de formato sobre `404`; `404` sobre `400` de padre y sobre el chequeo de mover; `404` sobre `409` de código; PST |
| 37 | MST, PST, MCIT y PCIT: 100 caracteres (con espacios alrededor) pasa y 101 da `400` en `name` |

Desvíos respecto del diseño:

- `MachineRepository` suma `existsById(long)`, que el diseño no listaba: `PartService`
  lo usa para el `404` de la máquina sin calcular su `partCount`.
- CORS ya permitía `PATCH`; no hizo falta tocar `CorsConfig`. Lo cubre
  `PartControllerIT` con un preflight.
- `MachineCode.isValid` valida sobre el texto recortado antes de pasarlo a
  mayúsculas y solo con ASCII (el diseño decía "sobre el valor normalizado"):
  así `ß`, que `toUpperCase` convierte en `SS`, queda rechazada.
- Los `POST` declaran el `201` en `@ApiResponse` para que aparezca en OpenAPI.
