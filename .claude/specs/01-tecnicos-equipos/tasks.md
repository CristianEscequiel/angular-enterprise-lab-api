# Tareas: Spec 01 — Técnicos y equipos

Entorno: compilar requiere JDK 21 (`JAVA_HOME`) y Docker para las IT. Toda tarea
deja `./gradlew test` en verde. Las IT comparten un único Postgres (ver
`design.md` §7): cada test usa legajos y equipos propios y los limpia al terminar.

- [x] 1. Migraciones `V2`, `V2_1` (seed dev) y `V3`, y ajuste de `MigrationIT`
  - Detalle: `db/migration/V2__maintenance_schema.sql` (columnas nulables en
    `technicians`, `teams`, `team_members` con `ON DELETE CASCADE` solo en
    `team_id`, `UNIQUE(team_id, technician_id)` e índice por `technician_id`),
    `db/seed/V2_1__seed_maintenance.sql` (UPDATE de `1001` y `1002`, INSERT de
    `1003`, los dos equipos y sus miembros por subselect) y
    `db/migration/V3__technicians_required_columns.sql` (`NOT NULL` y `CHECK`
    del formato del legajo). Van juntas porque `V3` fallaría en `dev` sin el
    seed en el medio (design.md §2.1 y §2.2). Se actualiza `MigrationIT`: las
    columnas esperadas de `technicians` y la fila de prueba, que pasa a llevar
    todos los campos y un legajo numérico.
  - Depende de: —
  - Verificación: `MaintenanceMigrationIT` — columnas de `technicians`; existen
    `teams` y `team_members`; borrar un técnico que es miembro viola la FK;
    insertar dos veces el par equipo-técnico viola el unique; borrar el equipo
    borra sus membresías. `SeedMaintenanceIT` (perfil `dev`) — 3 técnicos con
    sus datos, `1003` sin usuario, los 2 equipos con sus miembros. `MigrationIT`
    y `SeedUsersIT` siguen en verde.
  - _Requisitos: REQ-36, REQ-37_

- [x] 2. Prueba de migración sobre una base ya sembrada (`MaintenanceUpgradeIT`)
  - Detalle: JUnit sin contexto de Spring, en el paquete `com.enterpriselab.api`
    para usar el contenedor de `AbstractPostgresIT`. Crea una base vacía dentro
    de ese contenedor y usa la API de Flyway con `locations` de `db/migration`
    y `db/seed`.
  - Depende de: 1
  - Verificación: migrar con `target("1.1")`, guardar los `id` de `1001` y
    `1002` y los `technician_id` de `users`, migrar hasta el final: los ids y
    los vínculos no cambian, queda una fila por legajo y los datos están
    completos. Segundo test: migrar solo con `db/migration` deja `technicians`
    y `teams` vacías.
  - _Requisitos: REQ-36, REQ-37_

- [x] 3. Excepciones genéricas y su traducción HTTP
  - Detalle: en `shared/domain`, `ValidationFailedException(Map details)`,
    `InvalidReferenceException(code, message)`, `NotFoundException(message)` y
    `ConflictException(code, message)`. En `RestExceptionHandler`, un handler
    por cada una (`400`, `400`, `404`, `409`, con `details` solo en la primera)
    y uno para `HttpMessageNotReadableException` (`400 VALIDATION_ERROR`).
    `shared/web/AuthenticatedRole.from(Jwt)` devuelve el `Role` del claim
    `role`.
  - Depende de: —
  - Verificación: `RestExceptionHandlerTest` ampliado (y `TestExceptionsController`
    con un endpoint por excepción): cada una responde su HTTP, su `code` y la
    forma `{code,message,timestamp,path}`; un cuerpo JSON inválido responde
    `400 VALIDATION_ERROR`. `AuthenticatedRoleTest`: token con cada uno de los
    cuatro roles.
  - _Requisitos: REQ-6, REQ-7, REQ-27 (contrato de errores); habilita el resto_

- [x] 4. Tipos de dominio y matriz de permisos
  - Detalle: en `maintenance/domain`, `Specialty` y `TeamType` (con
    `toValue`/`fromValue`, mismo patrón que `Role`), `Legajo` (`isValid` con
    `matches("[0-9]{1,8}")` y `require`, que lanza `ValidationFailedException`
    con detalle `legajo`), los records `Technician`, `Team`,
    `TechnicianCommand` y `TeamCommand`, y `MaintenancePermissions` con las
    tres constantes de roles (design.md §3).
  - Depende de: 3
  - Verificación: `LegajoTest` — válidos (`1`, `12345678`, `0001`) e inválidos
    (`abc`, `123456789`, vacío, `" 1"`, `"1\n"`, dígitos no ASCII, `null`);
    `SpecialtyTest` y `TeamTypeTest` — ida y vuelta de cada valor y rechazo de
    mayúsculas, espacios y desconocidos; `MaintenancePermissionsTest` — la
    matriz coincide con `maintenance.permissions.ts`.
  - _Requisitos: REQ-7, REQ-9, REQ-18, REQ-19, REQ-35_

- [ ] 5. `TechnicianService` y su puerto
  - Detalle: `TechnicianRepository` (design.md §3) y `TechnicianService` con
    `list`, `get`, `create`, `update` y `delete`, siguiendo el orden de §4.1
    (rol → formato/validación con todos los errores juntos → existencia →
    integridad). Recorte de nombres, máximo 100, `legajo` del `PUT` nulo o
    igual al de la URL, y un solo `409 TECHNICIAN_IN_USE` con todos los
    motivos en el mensaje. Sin `@Transactional`. Queda **sin `@Service`** hasta la tarea 8: sin los adaptadores de los puertos el contexto de Spring no arranca.
  - Depende de: 4
  - Verificación: `TechnicianServiceTest` (Mockito sobre el puerto) —
    **un test por rol y acción** (4 roles × 5 operaciones) y que con `403` no
    se toca el puerto, ni con legajo `abc`; nombre ausente, vacío y solo
    espacios; 100 caracteres pasa y 101 falla; recorte al guardar; especialidad
    y tipo ausentes e inválidos; todos los errores en un solo `details`;
    `PUT` con otro `legajo` no guarda; baja con login, baja con equipos, baja
    con ambos (mensaje con los dos motivos); `PUT` inválido sobre legajo
    inexistente da `400` y no `404`.
  - _Requisitos: REQ-4, REQ-6, REQ-7, REQ-8, REQ-9, REQ-10, REQ-11, REQ-12,
    REQ-13, REQ-14, REQ-15, REQ-16, REQ-17, REQ-18, REQ-19, REQ-39_

- [ ] 6. `TeamService` y su puerto
  - Detalle: `TeamRepository` ya quedó definido en la tarea 5 (lo necesita
    `TechnicianService` para la baja); acá se usa. `TeamService` con `list`,
    `get`, `create`, `update` y `delete`. Un único validador de `memberLegajos`
    compartido por `create` y `update`: formato → repetidos → (solo si el resto
    está bien) existencia con `findExistingLegajos`. Id no numérico tratado como
    inexistente. `memberLegajos` nulo es error; `[]` es válido. Sin `@Service`
    hasta la tarea 8, igual que `TechnicianService`.
  - Depende de: 4
  - Verificación: `TeamServiceTest` (Mockito) — **un test por rol y acción**
    (4 roles × 5 operaciones); nombre y tipo inválidos, recorte y máximo de 100;
    formato inválido (incluido un `null` dentro de la lista), repetidos
    (`"0001"` y `"1"` no lo son) y legajo inexistente, **cada caso contra
    `create` y contra `update`**, sin llamar a `save`; el mensaje de
    `UNKNOWN_TECHNICIAN` lista todos los legajos faltantes; `memberLegajos`
    ausente da `400` y `[]` se acepta; id `abc` da `NotFoundException` en
    `get`, `update` y `delete`.
  - _Requisitos: REQ-20 a REQ-35, REQ-39, REQ-40, REQ-41_

- [ ] 7. Persistencia de técnicos
  - Detalle: mover `TechnicianEntity` de `auth/persistence` a
    `maintenance/persistence`, pública y con `firstName`, `lastName`,
    `specialty` y `teamType`; `UserEntity` solo cambia el `import`.
    `TechnicianJpaRepository` (paquete-privado, con `existsUserByTechnicianId`
    como `@Query` nativa para no importar `UserEntity`),
    `TechnicianRepositoryAdapter` (`@Transactional`), `TechnicianMapper` manual.
    El adaptador traduce por **nombre de constraint** `technicians_legajo_key`
    a `DUPLICATE_LEGAJO` y `users_technician_id_fkey` /
    `team_members_technician_id_fkey` (al borrar) a `TECHNICIAN_IN_USE`
    (design.md §6). Listado ordenado por `id`.
  - Depende de: 1, 3, 5
  - Verificación: `TechnicianRepositoryAdapterIT` — alta y lectura; edición de
    los cuatro campos sin tocar el legajo; `findExistingLegajos`;
    `hasLoginUser` verdadero para `1001` y falso para `1003`; dos `save` del
    mismo legajo que se saltean el chequeo del servicio dan
    `DUPLICATE_LEGAJO`; borrar `1001` da `TECHNICIAN_IN_USE`; otra violación de
    integridad no se traduce. `UserRepositoryAdapterIT` y `AuthControllerIT`
    siguen en verde con la entity movida.
  - _Requisitos: REQ-5, REQ-6, REQ-15, REQ-16, REQ-36_

- [ ] 8. Persistencia de equipos
  - Detalle: `TeamEntity` sin colección, `TeamMemberEntity` suelta,
    `TeamJpaRepository` y `TeamMemberJpaRepository` (`deleteByTeamId` con
    `@Modifying(flushAutomatically = true, clearAutomatically = true)` y el
    JPQL que trae `(teamId, legajo)` por `sort_order`), `TeamRepositoryAdapter`
    (`@Transactional`) y `TeamMapper`. `save` reemplaza los miembros borrando,
    haciendo `flush` e insertando con `sort_order = índice`.
    `findNamesByMemberLegajo`. Traducción de
    `team_members_technician_id_fkey` al insertar a `UNKNOWN_TECHNICIAN`.
    Listar equipos son 2 consultas, sin N+1. Con los dos adaptadores ya
    existentes, se anota `@Service` en `TechnicianService` y en `TeamService`
    (las tareas 5 y 6 los dejaron sin la anotación para no romper el arranque).
  - Depende de: 1, 3, 5, 6, 7
  - Verificación: `TeamRepositoryAdapterIT` — alta y lectura con orden
    `["1002","1001"]`; reemplazo que **conserva a un miembro y reordena** (el
    caso que fallaría con `orphanRemoval`); reemplazo por lista vacía; un mismo
    legajo en dos equipos; borrar el equipo deja al técnico; un técnico
    borrado justo antes de insertar da `UNKNOWN_TECHNICIAN`;
    `findNamesByMemberLegajo`.
  - _Requisitos: REQ-16, REQ-29, REQ-30, REQ-31, REQ-33, REQ-36_

- [ ] 9. `TechnicianController`
  - Detalle: `TechnicianRequest`, `TechnicianResponse` (con `id` como string y
    `from(Technician)`), y `TechnicianController` en `/technicians` con los cinco
    endpoints de design.md §5: `201` con `Location` en el alta, `204` en la baja.
    Solo extrae el rol con `AuthenticatedRole`, delega y mapea. `@Tag`,
    `@Operation`, `@ApiResponse` y `@SecurityRequirement(bearerAuth)`.
  - Depende de: 3, 5, 7, 8 (el servicio necesita también el adaptador de equipos)
  - Verificación: `TechnicianControllerIT`, con los usuarios del seed —
    listado (contiene `1001`, `1002` y `1003`), consulta de `1001` y de un
    inexistente (`404`); alta `201` y la cantidad de filas de `users` no cambia;
    alta duplicada `409 DUPLICATE_LEGAJO` sin fila nueva; legajo inválido en el
    cuerpo del `POST` y en la URL de `GET`, `PUT` y `DELETE` (`400` con detalle
    `legajo`); edición de los cuatro campos, `PUT` con otro `legajo` (`400`,
    técnico intacto) y sobre un inexistente (`404`, sin alta); alta + baja
    `204`; baja de inexistente `404`; baja de `1001` `409` (login) y de un
    técnico nuevo puesto en dos equipos `409` con los dos nombres; `produccion`
    y `tecnico` reciben `403` en todo, `teamleader` recibe `403` en el `DELETE`,
    y con `abc` en la URL el `403` gana al `400`; sin token, `401`; cuerpo JSON
    inválido, `400`.
  - _Requisitos: REQ-1 a REQ-19, REQ-39_

- [ ] 10. `TeamController`
  - Detalle: `TeamRequest`, `TeamResponse` y `TeamController` en `/teams` con
    los cinco endpoints de design.md §5, mismas convenciones que la tarea 9.
    `id` de la URL como `String`.
  - Depende de: 3, 6, 8
  - Verificación: `TeamControllerIT` — listado con los 2 equipos del seed,
    consulta de uno y de un inexistente; alta `201` con `id`; nombre vacío o
    solo espacios, recorte y tipo inválido; `memberLegajos` con formato
    inválido, inexistente (`UNKNOWN_TECHNICIAN` con el legajo en el mensaje) y
    repetidos, **cada uno contra `POST` y contra `PUT`**, y tras el `PUT`
    fallido el equipo queda idéntico; sin `memberLegajos` `400` y `[]` acepta;
    orden de los miembros; un legajo en dos equipos; `PUT` que reemplaza
    nombre, tipo y miembros, conservando a uno y reordenando; `PUT` sobre
    inexistente sin alta; baja `204` y los técnicos siguen; baja de
    inexistente `404`; `GET`, `PUT` y `DELETE` con id `abc` dan `404`;
    `administrador`, `produccion` y `tecnico` reciben `403` en las cinco
    operaciones.
  - _Requisitos: REQ-20 a REQ-35, REQ-40, REQ-41, REQ-39_

- [ ] 11. Documentación OpenAPI de `/technicians` y `/teams`
  - Detalle: sin código nuevo si las tareas 9 y 10 anotaron bien; esta tarea
    ajusta lo que falte.
  - Depende de: 9, 10
  - Verificación: `OpenApiIT` ampliado — `/v3/api-docs` contiene
    `/technicians`, `/technicians/{legajo}`, `/teams` y `/teams/{id}` con sus
    métodos y el esquema `bearerAuth`.
  - _Requisitos: REQ-38_

- [ ] 12. Cierre: documentación y verificación completa
  - Detalle: el README suma técnicos y equipos al seed de `dev` (revisar si lista
    datos); `CLAUDE.md` actualiza "Estado actual" (spec 01 implementada,
    próximo paso 00-A); `ROADMAP.md` marca la 01 como hecha; el texto de REQ-36
    pasa de "una migración" a "las migraciones" (design.md §2.2 y §8, punto 3).
    Recorrido de REQ-1 a REQ-41 con su evidencia.
  - Depende de: 1 a 11
  - Verificación: `./gradlew test` completo en verde; manual — con
    `docker compose up -d` y el perfil `dev`, `POST /auth/login` como
    `teamleader` y `GET /technicians` y `GET /teams` desde Swagger UI; el
    workflow de CI termina en verde en el PR.
  - _Requisitos: todos_

## Trazabilidad

| REQ | Tareas |
|---|---|
| 1, 2, 3, 4, 5 | 5, 7, 9 |
| 6 | 3, 5, 7, 9 |
| 7 | 3, 4, 5, 9 |
| 8, 10, 11, 12, 13 | 5, 9 |
| 9 | 4, 5, 9 |
| 14, 17 | 5, 9 |
| 15, 16 | 5, 7, 8, 9 |
| 18, 19 | 4, 5, 9 |
| 20 a 25 | 6, 10 |
| 26, 27, 28 | 6, 8, 10 |
| 29, 30 | 8, 10 |
| 31, 32 | 6, 8, 10 |
| 33, 34 | 6, 8, 10 |
| 35 | 4, 6, 10 |
| 36 | 1, 2, 7, 8, 12 |
| 37 | 1, 2 |
| 38 | 9, 10, 11 |
| 39 | 5, 6, 9, 10 |
| 40 | 6, 10 |
| 41 | 6, 10 |
