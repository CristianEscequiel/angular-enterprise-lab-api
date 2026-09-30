# Spec 01 — Diseño: técnicos y equipos

## 1. Decisiones técnicas

| Tema | Decisión | Motivo |
|---|---|---|
| Módulo | `maintenance` con `web / domain / persistence`, más `shared/domain` para las excepciones reutilizables | Organización vertical por feature (`CLAUDE.md`); las specs 02–04 van a necesitar las mismas excepciones |
| Autorización | `MaintenancePermissions` (matriz de roles) + `AccessPolicy.requireRole`, **dentro de los servicios de `domain`**. El controller solo extrae el rol del `Jwt` y lo pasa | Regla del proyecto y mismo patrón que la spec 00 |
| Validación de entrada | **En `domain`, después de autorizar.** Los request DTO son records planos (todo `String`, sin Bean Validation) y el servicio valida y devuelve el error | Fija el orden `401 → 403 → 400 → 404 → 409` para todas las operaciones. Con `@Valid` o `@Pattern` en el controller el `400` saldría **antes** del `403` (REQ-18 y REQ-19). Desvío consciente de `tech.md` ("Bean Validation en DTOs"); `LoginRequest` de la spec 00 no cambia |
| Enums en el request | `specialty`, `teamType` y `type` llegan como `String` y el dominio los parsea (`Specialty.fromValue`, `TeamType.fromValue`) | Un valor inválido tiene que ser `VALIDATION_ERROR` con detalle por campo, no un error de Jackson |
| Ids en la URL de equipos | `String` en el controller; si no es un número, el servicio lo trata como "no existe" (`404`) | Evita un `MethodArgumentTypeMismatchException`, que hoy caería en el `500` genérico |
| Ids en la respuesta | `id` como string (ROADMAP D2) | — |
| Miembros de un equipo | Tabla `team_members` con `id` propio, `UNIQUE(team_id, technician_id)` y `sort_order`; en la API viajan como `memberLegajos` | REQ-29 (orden) y REQ-36 (índice único por par). La API habla en legajos; la base, en FK |
| Reemplazo de miembros | El adaptador borra las filas del equipo, hace `flush` y recién ahí inserta las nuevas | Si se dejara a Hibernate (`orphanRemoval`), ejecuta los `INSERT` antes que los `DELETE` y el `UNIQUE(team_id, technician_id)` falla al reordenar o al conservar a un miembro |
| Mapeo | Mappers manuales (`TechnicianMapper`, `TeamMapper` en `persistence`; `from(...)` estático en los `Response`) | Igual que la spec 00: pocos tipos |
| Errores | 4 excepciones genéricas en `shared/domain` (una por categoría HTTP) con `code` propio, traducidas en `RestExceptionHandler` | Los `code` de esta spec (`DUPLICATE_LEGAJO`, `TECHNICIAN_IN_USE`, `UNKNOWN_TECHNICIAN`) son datos, no clases. Evita un handler por cada código nuevo de las specs siguientes |
| Orden de los listados | Por `id` ascendente (orden de alta) | Es lo que devolvía JSON Server |
| OpenAPI | `@Tag`, `@Operation`, `@ApiResponse` y `@SecurityRequirement(bearerAuth)` en los controllers | REQ-38; sin config nueva |

## 2. Modelo de datos y migraciones

### 2.1 Esquema

```sql
-- V2__maintenance_schema.sql  (db/migration)
ALTER TABLE technicians
    ADD COLUMN first_name VARCHAR(100),
    ADD COLUMN last_name  VARCHAR(100),
    ADD COLUMN specialty  VARCHAR(20) CHECK (specialty IN ('mecanico','electricista','general')),
    ADD COLUMN team_type  VARCHAR(30) CHECK (team_type IN ('guardia','preventivo-correctivo'));

CREATE TABLE teams (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    type  VARCHAR(30)  NOT NULL CHECK (type IN ('guardia','preventivo-correctivo'))
);

CREATE TABLE team_members (
    id            BIGSERIAL PRIMARY KEY,
    team_id       BIGINT  NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    technician_id BIGINT  NOT NULL REFERENCES technicians(id),      -- sin CASCADE (REQ-36)
    sort_order    INTEGER NOT NULL,
    CONSTRAINT team_members_team_technician_key UNIQUE (team_id, technician_id)
);
CREATE INDEX idx_team_members_technician ON team_members (technician_id);
```

```sql
-- V3__technicians_required_columns.sql  (db/migration)
ALTER TABLE technicians
    ALTER COLUMN first_name SET NOT NULL,
    ALTER COLUMN last_name  SET NOT NULL,
    ALTER COLUMN specialty  SET NOT NULL,
    ALTER COLUMN team_type  SET NOT NULL,
    ADD CONSTRAINT technicians_legajo_format CHECK (legajo ~ '^[0-9]{1,8}$');
```

- `team_members.team_id` sí lleva `ON DELETE CASCADE`: borrar un equipo borra sus membresías y deja a los técnicos intactos (REQ-33). Lo que no cascadea es el borrado de un **técnico**, que la FK bloquea.
- El índice sobre `technician_id` sirve a la consulta "¿de qué equipos es miembro?" (REQ-16); el `UNIQUE` empieza por `team_id` y no la cubre.
- No hay `UNIQUE(team_id, sort_order)`: el adaptador escribe siempre `0..n-1`, y ese unique complicaría el reemplazo sin aportar una regla del negocio.
- `first_name` y `last_name` de 100 y el `CHECK` de legajo de 1 a 8 dígitos son defensa en profundidad: el dominio valida primero (§4).

### 2.2 Orden de migraciones y seed (riesgo de REQ-36 / REQ-37)

Flyway ordena por versión entre **todas** las ubicaciones, y `V1_1` es la versión `1.1`. La secuencia resultante:

| Perfil | Migraciones que corren, en orden |
|---|---|
| `dev`, base nueva | `V1` → `V1_1` (seed spec 00) → `V2` → **`V2_1` (seed spec 01)** → `V3` |
| `dev`, base que ya tenía `V1` y `V1_1` | Pendientes: `V2` → `V2_1` → `V3` |
| resto de los perfiles | `V1` → `V2` → `V3` (sin filas, no hay nada que completar) |

- **`V2` agrega las columnas nulables y `V3` las hace obligatorias.** Entre las dos, `V2_1` (solo en `dev`) completa las filas que la spec 00 ya sembró. Así la migración de esquema no lleva datos de prueba ni valores inventados para poder cumplir el `NOT NULL`.
- `db/seed/V2_1__seed_maintenance.sql` (REQ-37):
  1. `UPDATE technicians ... WHERE legajo = '1001'` y lo mismo para `'1002'`: conservan su `id`, así que `users.technician_id` sigue apuntando bien (los usuarios `tecnico` y `electricista`).
  2. `INSERT` de `1003` (Marta Gómez, `general`, `preventivo-correctivo`), sin usuario.
  3. `INSERT` de los dos equipos y de sus miembros, resolviendo los ids con subselects por legajo y por nombre. Si faltara un técnico, el subselect da `NULL` y falla el `NOT NULL` de `team_members`: se prefiere un arranque roto a un seed a medias.
- **Si una base no-dev tuviera filas de `technicians` sin completar, `V3` falla al arrancar** con un error de Flyway explícito. Es el comportamiento buscado (fail fast); la spec 00 no expone ninguna forma de crear esas filas.
- `application.yml` no cambia: `db/seed` ya está en `spring.flyway.locations` del perfil `dev`.
- **Desvío de REQ-36 (resuelto al cerrar la spec):** el requisito decía "una migración" y el diseño usa dos (`V2` y `V3`) porque el seed tiene que caer en el medio; el texto de REQ-36 se ajustó a "las migraciones". Alternativa descartada: una sola migración con `DEFAULT ''` y `DROP DEFAULT`, que dejaría filas con nombre vacío (inválido según REQ-8) para cualquier fila preexistente.
- **Numeración:** esta spec toma `V2`, `V2_1` y `V3`. La spec 02 puede desarrollarse en paralelo, así que quien llegue segundo elige el siguiente número libre (`V4` o más) al implementar.

### 2.3 Impacto sobre lo ya construido (spec 00)

- `TechnicianEntity` **se mueve** de `auth/persistence` a `maintenance/persistence` (pasa a `public`, con los cuatro campos nuevos). Hay una sola entity por tabla; `UserEntity` solo cambia el `import`. Nadie más la usa (verificado con búsqueda en `src/`).
- `MigrationIT` **debe actualizarse**: hoy afirma que `technicians` tiene exactamente `id` y `legajo`, e inserta una fila con solo `legajo = 'LEG-MIGRATION-IT'`, que con `V3` viola el `NOT NULL` y el `CHECK` del formato.
- `RestExceptionHandler` suma los handlers de §5.

## 3. Componentes por capa

```
com.enterpriselab.api
├── shared/domain/       ValidationFailedException, NotFoundException,
│                        ConflictException, InvalidReferenceException
├── shared/web/          RestExceptionHandler (+4 handlers y HttpMessageNotReadable),
│                        AuthenticatedRole (Jwt → Role)
└── maintenance/
    ├── web/             TechnicianController, TechnicianRequest, TechnicianResponse,
    │                    TeamController, TeamRequest, TeamResponse
    ├── domain/          Technician, Team, Specialty, TeamType, Legajo,
    │                    TechnicianCommand, TeamCommand,
    │                    TechnicianService, TeamService, MaintenancePermissions,
    │                    TechnicianRepository, TeamRepository (puertos)
    └── persistence/     TechnicianEntity (movida), TeamEntity, TeamMemberEntity,
                         TechnicianJpaRepository, TeamJpaRepository,
                         TeamMemberJpaRepository (paquete-privados),
                         TechnicianRepositoryAdapter, TeamRepositoryAdapter,
                         TechnicianMapper, TeamMapper
```

**Dominio**

- `Technician(Long id, String legajo, String firstName, String lastName, Specialty specialty, TeamType teamType)` y `Team(Long id, String name, TeamType type, List<String> memberLegajos)`: records sin anotaciones de JPA. `Specialty` y `TeamType` siguen el patrón de `Role` (`toValue()` / `fromValue()` con el valor kebab-case).
- `Legajo`: `isValid(String)` con `matches("[0-9]{1,8}")` (no acepta espacios, saltos de línea ni dígitos no ASCII), y `require(String)`, que lanza `ValidationFailedException` con el detalle `legajo`. El legajo **no se recorta**: es identidad, y `" 1001"` es inválido.
- `TechnicianCommand(legajo, firstName, lastName, specialty, teamType)` y `TeamCommand(name, type, memberLegajos)`: la entrada cruda, tal como la manda el controller.
- `MaintenancePermissions`: tres constantes de `Role[]`, espejo de `maintenance.permissions.ts`.

  | Constante | Roles | Se usa en |
  |---|---|---|
  | `TECHNICIANS_READ_WRITE` | `ADMINISTRADOR`, `TEAM_LEADER_MANTENIMIENTO` | ver, crear y editar técnicos |
  | `TECHNICIANS_DELETE` | `ADMINISTRADOR` | baja de técnicos |
  | `TEAMS` | `TEAM_LEADER_MANTENIMIENTO` | todo `/teams` |

- Puertos:

  ```java
  interface TechnicianRepository {
      List<Technician> findAll();
      Optional<Technician> findByLegajo(String legajo);
      boolean existsByLegajo(String legajo);
      Set<String> findExistingLegajos(Collection<String> legajos);
      boolean hasLoginUser(String legajo);          // REQ-15
      Technician save(Technician technician);       // alta (id null) o edición
      void deleteByLegajo(String legajo);
  }
  interface TeamRepository {
      List<Team> findAll();
      Optional<Team> findById(long id);
      Team save(Team team);                          // alta (id null) o reemplazo total
      void deleteById(long id);
      List<String> findNamesByMemberLegajo(String legajo);   // REQ-16
  }
  ```

**Persistencia**

- `TeamEntity(id, name, type)` **sin** colección mapeada; `TeamMemberEntity(id, teamId, technicianId, sortOrder)` suelta. Los miembros se leen con un JPQL que une con `TechnicianEntity` y trae `(teamId, legajo)` ordenado por `sort_order`: listar equipos son **2 consultas** (equipos y miembros) sin N+1.
- `TeamMemberJpaRepository.deleteByTeamId` es un `@Modifying(flushAutomatically = true, clearAutomatically = true)`; `TeamRepositoryAdapter.save` lo llama y después inserta los miembros con `sort_order = índice`.
- `hasLoginUser` es una `@Query(nativeQuery = true)` sobre `users` (`select exists(select 1 from users u join technicians t on ... where t.legajo = :legajo)`): así `maintenance.persistence` **no importa** `UserEntity` y no aparece un ciclo entre `auth` y `maintenance`.
- Los adaptadores llevan `@Transactional` (con `readOnly` en las lecturas), como `UserRepositoryAdapter`. Los servicios de dominio no abren transacciones.

**Web**

- `TechnicianController` (`/technicians`) y `TeamController` (`/teams`) son finos: `AuthenticatedRole.from(jwt)` + delegar en el servicio + mapear a `Response`.
- `TechnicianRequest(legajo, firstName, lastName, specialty, teamType)` se usa para el `POST` y para el `PUT`; en el `PUT` el `legajo` es opcional (REQ-12). `TeamRequest(name, type, memberLegajos)`.
- `TechnicianResponse(id, legajo, firstName, lastName, specialty, teamType)` y `TeamResponse(id, name, type, memberLegajos)`, con `id` como string y los enums en su valor kebab-case.
- REQ-5: el módulo nunca escribe en `users`; solo la lectura de `hasLoginUser`.

## 4. Reglas de dominio

### 4.1 Orden de evaluación (igual en todas las operaciones)

| # | Paso | Falla con |
|---|---|---|
| 1 | Token válido | `401` (filtro de seguridad, spec 00) |
| 2 | Rol permitido (`AccessPolicy.requireRole`) | `403` — antes que cualquier otra cosa, incluido un legajo de URL mal formado |
| 3 | Formato del legajo de la URL y validación del cuerpo, **todos los errores juntos** en `details` | `400 VALIDATION_ERROR` |
| 4 | Existencia del recurso | `404 NOT_FOUND` |
| 5 | Reglas de integridad | `409` (o `400 UNKNOWN_TECHNICIAN`) |

Consecuencia: un `PUT` con cuerpo inválido sobre un legajo que no existe responde `400`, no `404`. La única excepción al orden es un JSON malformado o ausente: lo rechaza Spring al leer el cuerpo, antes de entrar al controller, y responde `400 VALIDATION_ERROR` (handler nuevo, §5) también a un usuario sin permiso.

### 4.2 `TechnicianService`

| Operación | Flujo | REQ |
|---|---|---|
| `list(actor)` | requireRole(`TECHNICIANS_READ_WRITE`) → `findAll` | 1, 18 |
| `get(actor, legajo)` | requireRole → `Legajo.require` → `findByLegajo` o `NotFoundException` | 2, 3, 7, 18 |
| `create(actor, cmd)` | requireRole → validar todo el comando → `existsByLegajo` (si existe, `ConflictException("DUPLICATE_LEGAJO")`) → `save` | 4–10, 18 |
| `update(actor, legajo, cmd)` | requireRole → `Legajo.require(legajo)` → validar campos y que `cmd.legajo` sea nulo o igual al de la URL → `findByLegajo` (404) → `save` con los 4 campos nuevos | 7–13, 18 |
| `delete(actor, legajo)` | requireRole(`TECHNICIANS_DELETE`) → `Legajo.require` → `findByLegajo` (404) → chequeos de baja → `deleteByLegajo` | 14–17, 19 |

Validaciones de campos (una sola pasada, en un `Map` campo → mensaje):

- `legajo` (solo en el alta): formato `Legajo` (REQ-7).
- `firstName` y `lastName`: `strip()`; no nulos ni vacíos (REQ-8); se guardan recortados (REQ-10); máximo 100 caracteres (ver §8).
- `specialty` y `teamType`: no nulos y exactamente uno de los valores válidos, sin `trim` ni cambio de mayúsculas (REQ-9).
- `PUT` con `legajo` distinto del de la URL: error en el campo `legajo` (REQ-12); se compara como texto exacto.

Chequeos de baja (REQ-15 y REQ-16): se evalúan **los dos** y el mensaje lista todos los motivos que apliquen (por ejemplo `"El técnico 1001 no se puede eliminar: tiene un usuario de acceso; es miembro de: Guardia mecánica"`). Un solo `ConflictException("TECHNICIAN_IN_USE")` si hay al menos uno.

### 4.3 `TeamService`

| Operación | Flujo | REQ |
|---|---|---|
| `list(actor)` / `get(actor, id)` | requireRole(`TEAMS`) → `findAll` / `findById` (id no numérico o inexistente → `NotFoundException`) | 20–22, 35 |
| `create(actor, cmd)` | requireRole → validar comando (incluye miembros) → `save` | 23–30, 35 |
| `update(actor, id, cmd)` | requireRole → validar comando (**mismo validador que el alta**) → `findById` (404) → `save` reemplazando nombre, tipo y miembros | 24–32, 35 |
| `delete(actor, id)` | requireRole → `findById` (404) → `deleteById` | 33–35 |

Validación de `memberLegajos`, un único método compartido por `create` y `update` (REQ-26, REQ-27, REQ-28 y REQ-31):

1. Formato: cada elemento (incluido un `null`) cumple `Legajo`; si no, `VALIDATION_ERROR` con detalle `memberLegajos`.
2. Repetidos: comparación de texto exacto (`"0001"` y `"1"` son legajos distintos); si hay alguno, `VALIDATION_ERROR` con detalle `memberLegajos`.
3. Solo si `name`, `type` y los pasos 1–2 están bien: `findExistingLegajos`; los que falten se informan juntos en `InvalidReferenceException("UNKNOWN_TECHNICIAN", "No existe el técnico con legajo 9999")`.

El `PUT` es todo o nada: como `save` corre después de todas las validaciones y en una sola transacción, un error deja nombre, tipo y miembros como estaban. Un mismo legajo puede estar en varios equipos (REQ-30): no hay ninguna restricción entre equipos distintos, solo `UNIQUE(team_id, technician_id)`.

`name`: `strip()`, obligatorio (REQ-24), máximo 100. `type`: uno de los dos valores (REQ-25). `memberLegajos` ausente (`null`) es error de validación; una lista vacía es válida (ver §8).

## 5. Contrato HTTP

| Método y ruta | Roles | Éxito | Errores propios |
|---|---|---|---|
| `GET /technicians` | admin, team leader | `200 [TechnicianResponse]` | — |
| `GET /technicians/{legajo}` | admin, team leader | `200 TechnicianResponse` | `400` legajo, `404` |
| `POST /technicians` | admin, team leader | `201 TechnicianResponse` + `Location: /technicians/{legajo}` | `400`, `409 DUPLICATE_LEGAJO` |
| `PUT /technicians/{legajo}` | admin, team leader | `200 TechnicianResponse` | `400`, `404` |
| `DELETE /technicians/{legajo}` | admin | `204` | `400` legajo, `404`, `409 TECHNICIAN_IN_USE` |
| `GET /teams` | team leader | `200 [TeamResponse]` | — |
| `GET /teams/{id}` | team leader | `200 TeamResponse` | `404` |
| `POST /teams` | team leader | `201 TeamResponse` + `Location: /teams/{id}` | `400`, `400 UNKNOWN_TECHNICIAN` |
| `PUT /teams/{id}` | team leader | `200 TeamResponse` | `400`, `400 UNKNOWN_TECHNICIAN`, `404` |
| `DELETE /teams/{id}` | team leader | `204` | `404` |

Todas devuelven `401` sin token y `403` con el rol equivocado, en el `ApiError` de la spec 00. `SecurityConfig` no cambia (`anyRequest().authenticated()`), y CORS ya permite `PUT` y `DELETE`.

Excepciones y su traducción en `RestExceptionHandler`:

| Excepción (`shared/domain`) | HTTP | `code` |
|---|---|---|
| `ValidationFailedException(Map details)` | 400 | `VALIDATION_ERROR`, con `details` por campo (mismo formato que `MethodArgumentNotValidException`) |
| `InvalidReferenceException(code, message)` | 400 | el que se le pase (`UNKNOWN_TECHNICIAN`) |
| `NotFoundException(message)` | 404 | `NOT_FOUND` |
| `ConflictException(code, message)` | 409 | el que se le pase (`DUPLICATE_LEGAJO`, `TECHNICIAN_IN_USE`) |
| `HttpMessageNotReadableException` (JSON inválido o sin cuerpo) | 400 | `VALIDATION_ERROR` |

Ejemplo de `400` de un alta con varios errores:

```json
{ "code": "VALIDATION_ERROR", "message": "La solicitud tiene datos inválidos",
  "timestamp": "2026-09-30T12:00:00Z", "path": "/technicians",
  "details": { "legajo": "Debe tener entre 1 y 8 dígitos", "firstName": "Es obligatorio" } }
```

**Observación fuera de alcance:** un método no soportado (por ejemplo `PATCH /technicians/1`) hoy cae en el `500 INTERNAL_ERROR` de la spec 00, porque no hay handler para `HttpRequestMethodNotSupportedException`. No lo arregla esta spec; queda anotado para el cierre de la 00 o la spec 06.

## 6. Concurrencia e integridad

Las verificaciones del servicio (`existsByLegajo`, `findExistingLegajos`, chequeos de baja) son previas y pueden quedar viejas entre el chequeo y la escritura. La base es la que manda, y el adaptador traduce las violaciones por **nombre de constraint** (no cualquier `DataIntegrityViolationException`, que taparía errores reales):

| Constraint violada | Se traduce a |
|---|---|
| `technicians_legajo_key` (alta simultánea del mismo legajo) | `ConflictException("DUPLICATE_LEGAJO")` |
| `team_members_technician_id_fkey` al insertar miembros (técnico borrado justo antes) | `InvalidReferenceException("UNKNOWN_TECHNICIAN")` |
| `team_members_technician_id_fkey` al borrar un técnico (lo agregaron a un equipo justo antes) | `ConflictException("TECHNICIAN_IN_USE")` |
| `users_technician_id_fkey` al borrar un técnico (le crearon un login justo antes) | `ConflictException("TECHNICIAN_IN_USE")` |

Cualquier otra violación sigue el camino normal al `500` sin filtrar detalles.

## 7. Estrategia de pruebas

Las IT extienden `AbstractPostgresIT` con perfil `dev` y los usuarios sembrados (`admin`, `teamleader`, `produccion`, `tecnico`). El contenedor y la base **se comparten entre todas las IT**, así que cada test crea sus propios legajos y equipos con valores únicos, los limpia al terminar y no afirma sobre totales del listado sino sobre lo que contiene.

| Requisito | Prueba |
|---|---|
| REQ-1, 2, 3 | `TechnicianControllerIT`: listado con los 3 técnicos del seed, consulta de `1001` y de un legajo inexistente |
| REQ-4, 5 | `TechnicianControllerIT`: `201` con el técnico creado; la cantidad de filas de `users` es la misma antes y después |
| REQ-6 | `TechnicianControllerIT` (409 `DUPLICATE_LEGAJO` y sin fila nueva) + `TechnicianRepositoryAdapterIT` (dos `save` que se saltean el chequeo previo: traduce `technicians_legajo_key`) |
| REQ-7 | `LegajoTest` (`abc`, `123456789`, `""`, `" 1"`, `"1\n"`, `"١٢٣"`) + `TechnicianControllerIT`: `400` en cuerpo del `POST` y en URL de `GET`, `PUT` y `DELETE`, sin ejecutar la operación |
| REQ-8, 9, 10 | `TechnicianServiceTest` (Mockito sobre los puertos): nombre vacío, solo espacios o ausente; especialidad y tipo ausentes o inválidos; recorte al guardar; todos los errores juntos en `details` |
| REQ-11, 12, 13 | `TechnicianControllerIT`: edición de los 4 campos; `PUT` con otro `legajo` (`400` y técnico intacto); `PUT` sobre un legajo inexistente (`404` y sin alta) |
| REQ-14, 17 | `TechnicianControllerIT`: alta + baja con `204`; baja de inexistente `404` |
| REQ-15 | `TechnicianControllerIT`: baja de `1001` (tiene login) `409 TECHNICIAN_IN_USE` y el técnico sigue existiendo |
| REQ-16 | `TechnicianControllerIT`: técnico nuevo en dos equipos nuevos, baja `409` con los dos nombres en el mensaje |
| REQ-18, 19 | `MaintenancePermissionsTest` + `TechnicianServiceTest`: **un test por rol y acción** (4 roles × 5 operaciones); `TechnicianControllerIT`: `produccion` y `tecnico` reciben `403`, y `teamleader` recibe `403` en el `DELETE`; con legajo `abc` en la URL el `403` gana al `400` |
| REQ-20, 21, 22 | `TeamControllerIT`: listado con los 2 equipos del seed, consulta de uno y de un id inexistente |
| REQ-39 | `TechnicianServiceTest` y `TeamServiceTest`: 100 caracteres pasa, 101 falla (tras recortar) |
| REQ-40 | `TeamServiceTest` + `TeamControllerIT`: sin `memberLegajos` `400` en `POST` y `PUT` (equipo intacto); `[]` acepta y deja el equipo sin miembros |
| REQ-41 | `TeamControllerIT`: `GET`, `PUT` y `DELETE` con `abc` responden `404` |
| REQ-23, 24, 25 | `TeamControllerIT` + `TeamServiceTest`: alta con `201` e `id`; nombre vacío o solo espacios; nombre recortado; tipo inválido |
| REQ-26, 27, 28 | `TeamServiceTest` + `TeamControllerIT`, **cada caso contra `POST` y contra `PUT`**: formato inválido, legajo inexistente (`UNKNOWN_TECHNICIAN` con el legajo en el mensaje), repetidos; el equipo queda sin cambios |
| REQ-29 | `TeamControllerIT`: alta con `["1002","1001"]` y lectura en el mismo orden; `PUT` que reordena |
| REQ-30 | `TeamControllerIT`: el mismo legajo en dos equipos |
| REQ-31, 32 | `TeamControllerIT`: `PUT` reemplaza nombre, tipo y miembros (incluyendo conservar a uno y reordenar, que es lo que dispararía el problema de `flush`); `PUT` que falla no cambia nada; `PUT` sobre id inexistente sin alta |
| REQ-33, 34 | `TeamControllerIT`: baja con `204`, los técnicos siguen existiendo; baja de inexistente `404` |
| REQ-35 | `TeamServiceTest` (4 roles × 5 operaciones) + `TeamControllerIT`: `administrador` recibe `403` |
| REQ-36 | `MaintenanceMigrationIT`: columnas de `technicians`, existencia de `teams` y `team_members`, borrar un técnico que es miembro viola la FK, insertar dos veces el par equipo-técnico viola el unique, borrar el equipo borra sus membresías |
| REQ-36, 37 | `MaintenanceUpgradeIT` (JUnit + API de Flyway, sin contexto Spring): crea una base vacía dentro del contenedor compartido, migra hasta `target("1.1")`, guarda los `id` de `1001` y `1002` y los `technician_id` de `users`, migra al final y verifica que los ids no cambiaron, que hay una fila por legajo y que los datos quedaron completos. Un segundo test migra solo con `db/migration` y verifica que `technicians` y `teams` quedan vacías (no se cargan fuera de `dev`) |
| REQ-37 | `SeedMaintenanceIT`: con `dev`, los 3 técnicos y los 2 equipos con sus miembros, y `1003` sin usuario |
| REQ-38 | `OpenApiIT` (se amplía): `/v3/api-docs` contiene `/technicians`, `/technicians/{legajo}`, `/teams` y `/teams/{id}` con sus métodos, y `bearerAuth` |

Tests de la spec 00 que se tocan: `MigrationIT` (§2.3). Debe seguir pasando todo lo demás con `./gradlew test`.

## 8. Puntos que decidí yo y reglas que los requisitos no cubren

Decisiones de diseño a confirmar al aprobar:

1. **Validación en `domain` y no con Bean Validation** (§1): garantiza `403` antes que `400` también para el cuerpo, no solo para el legajo de la URL. Costo: se aparta de lo que dice `tech.md` y hay que testear los mensajes de validación como unitarios.
2. **Validación antes que existencia:** `PUT` con cuerpo inválido sobre un legajo inexistente responde `400`.
3. **`V2` + `V3` en lugar de una sola migración** (§2.2), con el ajuste de texto de REQ-36 al cerrar.
4. **`TechnicianEntity` pasa a `maintenance/persistence`** y `auth` la importa (§2.3).
5. **Baja de técnico con varios motivos:** un solo `409` con todos los motivos en el mensaje (§4.2).

Comportamientos que aparecieron al diseñar y que ahora son requisitos (enmienda a `requirements.md`, sin renumerar):

| REQ | Regla | Qué hace el diseño |
|---|---|---|
| REQ-39 | Largo máximo de `firstName`, `lastName` y `name` de equipo | 100 caracteres tras recortar; más que eso es `400 VALIDATION_ERROR` en el campo (sin la regla, llegaría a la base y respondería `500`) |
| REQ-40 | `memberLegajos` ausente o vacío | Ausente (`null`) es `400`; `[]` es válido (equipo sin miembros) |
| REQ-41 | Id de equipo que no es un número | `404`, igual que un id inexistente |

## 9. Trazabilidad

| REQ | Sección |
|---|---|
| 1 | §3, §4.2, §5 |
| 2, 3 | §4.2, §5 |
| 4 | §4.2, §5 |
| 5 | §3 (el módulo no escribe en `users`), §7 |
| 6 | §4.2, §6 |
| 7 | §3 (`Legajo`), §4.1, §4.2 |
| 8, 9, 10 | §4.2 |
| 11, 12, 13 | §4.2 |
| 14, 17 | §4.2, §5 |
| 15, 16 | §3 (`hasLoginUser`, `findNamesByMemberLegajo`), §4.2, §6 |
| 18, 19 | §3 (`MaintenancePermissions`), §4.1 |
| 20, 21, 22 | §4.3, §5 |
| 23, 24, 25 | §4.3 |
| 26, 27, 28 | §4.3 |
| 29 | §1 y §2.1 (`sort_order`), §3 |
| 30 | §2.1 (unique por par y no por técnico), §4.3 |
| 31, 32 | §1 (reemplazo con `flush`), §4.3 |
| 33, 34 | §2.1 (`ON DELETE CASCADE` de `team_id`), §4.3 |
| 35 | §3 (`MaintenancePermissions`), §4.1 |
| 36 | §2.1, §2.2 |
| 37 | §2.2 |
| 38 | §1, §5 |
| 39 | §2.1 (`VARCHAR(100)`), §4.2, §4.3 |
| 40 | §4.3 |
| 41 | §1 (ids en la URL), §4.3 |
