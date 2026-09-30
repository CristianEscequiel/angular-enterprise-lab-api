# Spec 02 — Diseño: máquinas y árbol de partes

## 1. Decisiones técnicas

| Tema | Decisión | Motivo |
|---|---|---|
| Módulo | `machines` con `web / domain / persistence`, propio y al mismo nivel que `maintenance` | Organización vertical por feature (`CLAUDE.md`, `steering/structure.md`) |
| Autorización | `MachinesPermissions` en `domain`: `requireRead` (los cuatro roles) y `requireWrite` (`ADMINISTRADOR`, `TEAM_LEADER_MANTENIMIENTO`), sobre `AccessPolicy.requireRole`. El controller solo extrae el rol del `Jwt` | Regla del proyecto; mismo patrón que `MaintenancePermissions` |
| Validación de entrada | En `domain`, después de autorizar; los request son records planos de `String`, sin Bean Validation | Mismo desvío consciente de la spec 01: con `@Valid` el `400` saldría antes del `403` (REQ-31, REQ-36) |
| Orden de errores | `401 → 403 → 400 formato → 404 recurso de la URL → 400 referencia → 409` (REQ-36) | Extiende el orden de `steering/tech.md` con el `400` de referencia del padre, que necesita consultar la base y por eso va después del `404` |
| Ids | `BIGSERIAL` en la base, `string` en la API (ROADMAP D2). Un id de la URL que no es un número de hasta 18 dígitos es un recurso que no existe (`404`) | Mismo criterio que `TeamService.find` (REQ-41 de la spec 01); evita el `500` de un `MethodArgumentTypeMismatchException` |
| `parentId` del alta | `null` o ausente = primera nivel. Cualquier otro valor debe ser el id de una parte: si no es un número o no existe → `PARENT_PART_NOT_FOUND` (incluye `""`) | Un solo camino para "no resuelve a una parte" (REQ-21) |
| `PATCH` de una parte | Request con `name`, `machineId` y `parentId`, que **registra si cada campo vino** (setters de Jackson con una marca `sent`). Un campo enviado y distinto del actual es `400 VALIDATION_ERROR` (REQ-25) | Con un record plano `parentId: null` y "ausente" son indistinguibles, y mandar `null` a una sub-parte sería un intento de moverla a primer nivel que quedaría ignorado |
| Comparación de `machineId`/`parentId` en el `PATCH` | Texto exacto contra el id actual como string; un `parentId` `null` enviado contra una parte de primer nivel es igual al actual | — |
| `partCount` | Lo calcula la consulta (`LEFT JOIN` + `COUNT`), no una columna | Nunca queda desactualizado; las máquinas son pocas y sin paginar (ROADMAP D8) |
| Orden de los listados | Por `id` ascendente (orden de creación), tanto máquinas como partes | REQ-16; es lo que devolvía JSON Server |
| Integridad del árbol | La base lo garantiza además del dominio: FK compuesta del padre (misma máquina), sin cascada (REQ-33) | Un bug en el servicio no puede dejar una parte cruzada entre máquinas |
| Mapeo | Mappers manuales en `persistence`, `from(...)` estático en los `Response` | Igual que las specs 00 y 01 |
| Errores | Las 4 excepciones genéricas de `shared/domain` (`ValidationFailedException`, `NotFoundException`, `ConflictException`, `InvalidReferenceException`) con `code` propio; **no hay cambios en `RestExceptionHandler`** | Los `code` de esta spec son datos, no clases |
| OpenAPI | `@Tag`, `@Operation`, `@ApiResponse` y `@SecurityRequirement(bearerAuth)` en los controllers | REQ-35; sin config nueva |

## 2. Modelo de datos y migraciones

### 2.1 Esquema

```sql
-- V6__machines_schema.sql  (db/migration)
CREATE TABLE machines (
    id    BIGSERIAL PRIMARY KEY,
    code  VARCHAR(20)  NOT NULL,
    name  VARCHAR(100) NOT NULL,
    CONSTRAINT machines_code_key    UNIQUE (code),
    CONSTRAINT machines_code_format CHECK (code ~ '^[A-Z0-9][A-Z0-9-]{0,19}$')
);

CREATE TABLE parts (
    id          BIGSERIAL PRIMARY KEY,
    machine_id  BIGINT       NOT NULL REFERENCES machines(id),       -- sin CASCADE
    parent_id   BIGINT,
    name        VARCHAR(100) NOT NULL,
    CONSTRAINT parts_id_machine_key UNIQUE (id, machine_id),
    CONSTRAINT parts_parent_same_machine_fkey
        FOREIGN KEY (parent_id, machine_id) REFERENCES parts (id, machine_id)   -- sin CASCADE
);
CREATE INDEX idx_parts_machine ON parts (machine_id);
CREATE INDEX idx_parts_parent  ON parts (parent_id);
```

- `machines.code` es `UNIQUE` sobre el valor ya normalizado (mayúsculas y recortado): `env-01` y `ENV-01` llegan a la base como `ENV-01`. El `CHECK` repite el patrón de REQ-6 como defensa en profundidad.
- **FK compuesta del padre:** `(parent_id, machine_id)` apunta a `parts(id, machine_id)`. Con `MATCH SIMPLE` (el default) una fila con `parent_id NULL` no se comprueba, y una con padre exige que el padre exista **y** sea de la misma máquina. Es más fuerte que una FK simple y cubre REQ-22 y REQ-33 a la vez. El `UNIQUE (id, machine_id)` existe solo para ser el destino de esa FK.
- Ninguna FK lleva `ON DELETE CASCADE`: borrar una máquina con partes o una parte con hijos falla en la base (REQ-33), y el dominio se adelanta con un `409` legible.
- El índice de `parent_id` sirve a "¿tiene hijos?" (REQ-28); el de `machine_id`, a listar y contar partes por máquina. El `UNIQUE (id, machine_id)` empieza por `id` y no cubre ninguno de los dos.
- `name` de 100 es defensa en profundidad de la regla del §8 (punto 4).

### 2.2 Seed de dev (REQ-34) y numeración

| Perfil | Migraciones que corren, en orden |
|---|---|
| `dev` | `V1` → `V1_1` → `V2` → `V2_1` → `V3` → `V4` → `V4_1` → `V5` → `V6` → **`V6_1` (seed)** |
| resto de los perfiles | `V1` … `V5` → `V6` (sin filas) |

- `db/seed/V6_1__seed_machines.sql` se carga solo en `dev` (`db/seed` ya está en `spring.flyway.locations` de ese perfil; `application.yml` no cambia). Va después de `V6` porque necesita las tablas y no toca datos de otras specs.
- **Ids explícitos.** El `INSERT` fija los ids de `db.json` (máquinas `1` a `3`, partes `1` a `10`), porque las órdenes de prueba de la spec 03 los referencian. Como `BIGSERIAL` no avanza solo con ids explícitos, el script termina con `setval` de las dos secuencias al mayor id cargado, para que el próximo alta sea `4` y `11` (REQ-34, "continuando después del mayor"):

  ```sql
  SELECT setval(pg_get_serial_sequence('machines', 'id'), (SELECT MAX(id) FROM machines));
  SELECT setval(pg_get_serial_sequence('parts',    'id'), (SELECT MAX(id) FROM parts));
  ```

- Las partes se insertan **padres antes que hijos** (orden de id creciente dentro de cada máquina), porque la FK compuesta se evalúa fila por fila.
- Los nombres y la forma exacta del árbol salen de `db.json` del frontend (repo separado): la tarea de seed los copia de ahí, sin inventarlos. La forma fijada por REQ-34 es: Envasadora con cuatro niveles y una hoja hermana en el nivel 2, Selladora con dos niveles, Rotuladora sin partes, diez partes en total.
- **Numeración:** esta spec toma `V6` y `V6_1`. La spec 03 debe numerar desde `V7`.

### 2.3 Impacto sobre lo ya construido

- Ninguna entity ni servicio existente cambia. `MigrationIT` no afirma sobre tablas nuevas (verificar al implementar: si lista todas las tablas, se actualiza).
- `RestExceptionHandler`, `SecurityConfig` y CORS no cambian (`anyRequest().authenticated()`; CORS ya permite `PUT` y `DELETE`). **`PATCH` hay que agregarlo a los métodos permitidos de CORS** si no está: se verifica en la tarea del controller (hoy ninguna ruta lo usa).

## 3. Componentes por capa

```
com.enterpriselab.api.machines
├── web/          MachineController, PartController,
│                 MachineRequest, MachineResponse,
│                 PartRequest, PartPatchRequest, PartResponse
├── domain/       Machine, Part, MachineCode,
│                 MachineCommand, PartCommand, PartPatchCommand,
│                 MachineService, PartService, MachinesPermissions,
│                 MachineRepository, PartRepository (puertos)
└── persistence/  MachineEntity, PartEntity,
                  MachineJpaRepository, PartJpaRepository (paquete-privados),
                  MachineRepositoryAdapter, PartRepositoryAdapter,
                  MachineMapper, PartMapper, ConstraintViolations (copia local, ver nota)
```

Nota: `ConstraintViolations` de `maintenance/persistence` es paquete-privada. Se **mueve a `shared/persistence`** (pública) en vez de duplicarla; `maintenance` solo cambia el `import`. Es el único cambio en código de la spec 01.

**Dominio**

- `Machine(Long id, String code, String name, int partCount)` y `Part(Long id, long machineId, Long parentId, String name)`: records sin anotaciones de JPA.
- `MachineCode`: `normalize(String)` hace `strip()` + `toUpperCase(Locale.ROOT)`; `isValid(String)` aplica `^[A-Z0-9][A-Z0-9-]{0,19}$` **sobre el valor normalizado** (REQ-5, REQ-6). `toUpperCase(Locale.ROOT)` evita el problema de la `i` turca; un carácter que solo es válido por mayúscula Unicode (`ß` → `SS`) se descarta con un test.
- `MachineCommand(code, name)`, `PartCommand(name, parentId)` y `PartPatchCommand(name, machineIdSent, machineId, parentIdSent, parentId)`: la entrada cruda del controller.
- `MachinesPermissions`:

  | Método | Roles | Se usa en |
  |---|---|---|
  | `requireRead` | los cuatro | `GET /machines`, `GET /machines/{id}`, `GET /machines/{machineId}/parts` |
  | `requireWrite` | `ADMINISTRADOR`, `TEAM_LEADER_MANTENIMIENTO` | todo `POST`, `PUT`, `PATCH`, `DELETE` |

  `requireRead` lista los roles con `Role.values()`: si se agrega un rol nuevo hay que decidir a propósito si lee. Un rol `null` (token sin rol válido) da `403`.

- Puertos:

  ```java
  interface MachineRepository {
      List<Machine> findAll();                              // con partCount, por id
      Optional<Machine> findById(long id);                  // con partCount
      boolean existsByCode(String code);                    // alta
      boolean existsByCodeAndIdNot(String code, long id);   // edición (REQ-10, REQ-11)
      Machine save(Machine machine);                        // alta (id null) o edición de code y name
      int countParts(long machineId);                       // REQ-14
      void deleteById(long id);
  }
  interface PartRepository {
      List<Part> findByMachineId(long machineId);           // por id
      Optional<Part> findById(long id);
      Part save(Part part);                                 // alta (id null) o cambio de name
      int countChildren(long partId);                       // REQ-28
      void deleteById(long id);
  }
  ```

**Persistencia**

- `MachineEntity(id, code, name)` y `PartEntity(id, machineId, parentId, name)` con los ids como `Long` sueltos, **sin** relaciones `@ManyToOne` ni colecciones: el árbol es una lista plana y así no hay carga perezosa ni N+1.
- `partCount` sale de un JPQL de proyección (`select m.id, m.code, m.name, count(p) from MachineEntity m left join PartEntity p on p.machineId = m.id group by m.id, m.code, m.name order by m.id`): listar es **1 consulta**.
- Los adaptadores llevan `@Transactional` (con `readOnly` en las lecturas) y hacen `saveAndFlush` / `flush` para que la base tenga la última palabra (§6). Los servicios de dominio no abren transacciones.

**Web**

- `MachineController` (`/machines`) y `PartController` (`/parts` y `/machines/{machineId}/parts`; las dos rutas de partes viven en una sola clase para no partir el recurso) son finos: `AuthenticatedRole.from(jwt)` + delegar + mapear a `Response`.
- `MachineRequest(code, name)` y `PartRequest(name, parentId)` son records de `String`. `PartPatchRequest` es una clase con los tres campos y los setters que marcan `sent`; `toCommand()` arma el `PartPatchCommand`.
- `MachineResponse(id, code, name, partCount)` y `PartResponse(id, machineId, parentId, name)`: ids como string, `parentId` `null` en primer nivel.
- `POST` responde `201` con `Location` (`/machines/{id}`, `/parts/{id}`).

## 4. Reglas de dominio

### 4.1 Orden de evaluación (REQ-36)

| # | Paso | Falla con |
|---|---|---|
| 1 | Token válido | `401` (filtro de seguridad) |
| 2 | Rol permitido (`MachinesPermissions`) | `403` — antes que cualquier otra cosa, incluido un id de URL mal formado |
| 3 | Validación del cuerpo (`code`, `name`), **todos los errores juntos** en `details` | `400 VALIDATION_ERROR` |
| 4 | Existencia del recurso de la URL (máquina o parte) | `404 NOT_FOUND` |
| 5 | Referencias: padre (`PARENT_PART_NOT_FOUND`, `PARENT_PART_OTHER_MACHINE`) y "no se mueve" (REQ-25) | `400` |
| 6 | Reglas de integridad: código duplicado, baja con dependientes | `409` |

Consecuencias: un `PUT` con `name` vacío sobre una máquina inexistente responde `400`, no `404`; un alta de parte con `parentId` inexistente en una máquina inexistente responde `404`. El JSON malformado o ausente lo rechaza Spring antes del controller, como en la spec 01.

### 4.2 `MachineService`

| Operación | Flujo | REQ |
|---|---|---|
| `list(actor)` | `requireRead` → `findAll` | 1, 32 |
| `get(actor, id)` | `requireRead` → `find(id)` (404) | 2, 3, 32 |
| `create(actor, cmd)` | `requireWrite` → validar → `existsByCode` (409 `DUPLICATE_MACHINE_CODE`) → `save` → releer con `partCount` 0 | 4–8, 31 |
| `update(actor, id, cmd)` | `requireWrite` → validar → `find(id)` (404) → `existsByCodeAndIdNot` (409) → `save` de `code` y `name` | 9–12, 31 |
| `delete(actor, id)` | `requireWrite` → `find(id)` (404) → `countParts` > 0 → `ConflictException("MACHINE_HAS_PARTS")` → `deleteById` | 13–15, 31 |

Validación de campos (una sola pasada, `Map` campo → mensaje):

- `code`: obligatorio; se normaliza y se valida con `MachineCode` (REQ-5, REQ-6); el error del campo `code` incluye el patrón esperado.
- `name`: `strip()`, no nulo ni vacío, máximo 100 (REQ-8 y §8).

`update` compara el `code` normalizado contra las demás máquinas (`existsByCodeAndIdNot`): cambiar solo la capitalización de su propio código es válido (REQ-10). `MACHINE_HAS_PARTS` dice la cantidad en el `message`: `"La máquina ENV-01 no se puede eliminar: tiene 7 partes"` (singular "1 parte").

### 4.3 `PartService`

| Operación | Flujo | REQ |
|---|---|---|
| `listByMachine(actor, machineId)` | `requireRead` → `findMachine` (404) → `findByMachineId` | 16, 17, 30, 32 |
| `create(actor, machineId, cmd)` | `requireWrite` → validar `name` → `findMachine` (404) → `resolveParent` → `save` | 18–23, 31, 36 |
| `rename(actor, id, cmd)` | `requireWrite` → validar `name` → `find(id)` (404) → comprobar que no se mueve (400) → `save` del nombre | 23–26, 31, 36 |
| `delete(actor, id)` | `requireWrite` → `find(id)` (404) → `countChildren` > 0 → `ConflictException("PART_HAS_CHILDREN")` → `deleteById` | 27–29, 31 |

- `findMachine(machineId)` es una lectura de existencia sobre `MachineRepository`; `PartService` depende de ese puerto (no de JPA).
- `resolveParent(machineId, parentId)`: `null` → primer nivel. Si no, `parentId` no numérico o sin fila → `InvalidReferenceException("PARENT_PART_NOT_FOUND")`; si la fila tiene otra `machineId` → `InvalidReferenceException("PARENT_PART_OTHER_MACHINE")` (REQ-21, REQ-22).
- `rename` exige `name` (REQ-23). Un `machineId` enviado y distinto del de la parte, o un `parentId` enviado y distinto del actual, da `ValidationFailedException` con el detalle en el campo correspondiente (REQ-25); si coinciden con los actuales se ignoran. Como lo pide REQ-36, esto va **después** del `404`.
- `PART_HAS_CHILDREN` dice la cantidad en el `message`: `"La parte «Motor» no se puede eliminar: tiene 2 sub-partes"`.
- Reconstruir un árbol de cinco niveles (REQ-30) no necesita lógica: cada alta valida solo a su padre y el listado devuelve la lista plana.

## 5. Contrato HTTP

| Método y ruta | Roles | Éxito | Errores propios |
|---|---|---|---|
| `GET /machines` | los cuatro | `200 [MachineResponse]` | — |
| `GET /machines/{id}` | los cuatro | `200 MachineResponse` | `404` |
| `POST /machines` | admin, team leader | `201 MachineResponse` + `Location` | `400`, `409 DUPLICATE_MACHINE_CODE` |
| `PUT /machines/{id}` | admin, team leader | `200 MachineResponse` | `400`, `404`, `409 DUPLICATE_MACHINE_CODE` |
| `DELETE /machines/{id}` | admin, team leader | `204` | `404`, `409 MACHINE_HAS_PARTS` |
| `GET /machines/{machineId}/parts` | los cuatro | `200 [PartResponse]` | `404` |
| `POST /machines/{machineId}/parts` | admin, team leader | `201 PartResponse` + `Location` | `400`, `404`, `400 PARENT_PART_NOT_FOUND`, `400 PARENT_PART_OTHER_MACHINE` |
| `PATCH /parts/{id}` | admin, team leader | `200 PartResponse` | `400` (incluye intento de mover), `404` |
| `DELETE /parts/{id}` | admin, team leader | `204` | `404`, `409 PART_HAS_CHILDREN` |

Todas devuelven `401` sin token y `403` con el rol equivocado, en el `ApiError` de la spec 00. Los `409` llevan la cantidad de dependientes en `message`, no en `details` (`details` es solo de `VALIDATION_ERROR`, como en la spec 01).

## 6. Concurrencia e integridad

Las verificaciones del servicio (`existsByCode…`, `countParts`, `countChildren`, padre) son previas y pueden quedar viejas. La base manda y el adaptador traduce por **nombre de constraint**, con `ConstraintViolations` (no cualquier `DataIntegrityViolationException`):

| Constraint violada | Operación | Se traduce a |
|---|---|---|
| `machines_code_key` | alta o edición de máquina | `ConflictException("DUPLICATE_MACHINE_CODE")` |
| `parts_machine_id_fkey` | borrar una máquina a la que le agregaron una parte justo antes | `ConflictException("MACHINE_HAS_PARTS")` |
| `parts_parent_same_machine_fkey` | borrar una parte a la que le agregaron un hijo justo antes | `ConflictException("PART_HAS_CHILDREN")` |
| `parts_parent_same_machine_fkey` | insertar una parte cuyo padre fue borrado justo antes | `InvalidReferenceException("PARENT_PART_NOT_FOUND")` |
| `parts_machine_id_fkey` | insertar una parte de una máquina borrada justo antes | `NotFoundException` (la máquina ya no existe) |

La misma constraint `parts_parent_same_machine_fkey` se traduce según la operación (insertar → referencia; borrar → conflicto). Cualquier otra violación sigue al `500` sin filtrar detalles.

## 7. Estrategia de pruebas

Las IT extienden `AbstractPostgresIT` con perfil `dev` y los usuarios sembrados (`admin`, `teamleader`, `produccion`, `tecnico`). La base se comparte entre IT: cada test crea sus propios códigos y partes con valores únicos, los limpia al terminar y no afirma sobre totales del listado sino sobre lo que contiene.

| Requisito | Prueba |
|---|---|
| REQ-1, 2, 3 | `MachineControllerIT`: listado con las 3 máquinas del seed y su `partCount`; consulta de `1` y de un id inexistente o no numérico |
| REQ-4, 9 | `MachineControllerIT`: `201` con `id` generado y `partCount` 0; edición de `code` y `name` sin tocar `id` ni partes |
| REQ-5, 6 | `MachineCodeTest` (`" env-01 "` → `ENV-01`; vacío, 21 caracteres, `-A`, `A B`, `A_1`, `ß`) + `MachineControllerIT`: `400` con detalle `code` |
| REQ-7, 11 | `MachineControllerIT` (`409 DUPLICATE_MACHINE_CODE` con `env-01` contra `ENV-01`, sin cambios) + `MachineRepositoryAdapterIT` (dos `save` que se saltean el chequeo previo: traduce `machines_code_key`) |
| REQ-8, 23 | `MachineServiceTest` y `PartServiceTest` (Mockito sobre los puertos): nombre ausente, vacío o solo espacios; recorte; todos los errores juntos en `details` |
| REQ-10 | `MachineServiceTest` + `MachineControllerIT`: `PUT` con el mismo código en otra capitalización |
| REQ-12, 15, 17, 20, 26, 29 | `MachineControllerIT` y `PartControllerIT`: `404` y sin filas nuevas en `PUT`, `POST` de parte y `PATCH` |
| REQ-13, 14 | `MachineControllerIT`: baja de una máquina nueva `204`; baja de `1` (Envasadora) `409 MACHINE_HAS_PARTS` con la cantidad en el mensaje y las partes intactas |
| REQ-16, 30 | `PartControllerIT`: listado de la Envasadora en orden de creación; árbol de 5 niveles creado por la API y reconstruido desde el listado |
| REQ-18, 19 | `PartControllerIT`: alta de primer nivel (`parentId` nulo y ausente) y de sub-parte |
| REQ-21, 22 | `PartControllerIT` + `PartServiceTest`: `parentId` inexistente, no numérico y `""` → `PARENT_PART_NOT_FOUND`; padre de otra máquina → `PARENT_PART_OTHER_MACHINE`; nada creado |
| REQ-24, 25 | `PartControllerIT` + `PartServiceTest`: `PATCH` cambia solo el nombre; `machineId`/`parentId` distintos → `400` y parte intacta; iguales o ausentes se ignoran; `parentId: null` enviado a una sub-parte → `400` |
| REQ-27, 28 | `PartControllerIT`: baja de una hoja `204`; baja de un padre `409 PART_HAS_CHILDREN` con la cantidad y el subárbol intacto; baja del subárbol de hojas hacia arriba |
| REQ-31 | `MachinesPermissionsTest` + `*ServiceTest`: **un test por rol y operación de escritura** (4 roles × 7 operaciones); `*ControllerIT`: `produccion` y `tecnico` reciben `403` en `POST`, `PUT`, `PATCH` y `DELETE`, y el `403` gana a un id de URL inválido |
| REQ-32 | `*ControllerIT`: los cuatro roles reciben `200` en las tres lecturas |
| REQ-33 | `MachinesMigrationIT`: `code` único, `CHECK` de formato, una parte con máquina o padre inexistente viola la FK, **un padre de otra máquina viola `parts_parent_same_machine_fkey`**, borrar una máquina con partes o una parte con hijos viola la FK |
| REQ-34 | `SeedMachinesIT`: con `dev`, las 3 máquinas y las 10 partes con los ids de `db.json`, la forma del árbol, y el alta siguiente con id `4` (máquina) y `11` (parte); un segundo test migra solo con `db/migration` y verifica las dos tablas vacías |
| REQ-35 | `OpenApiIT` (se amplía): `/v3/api-docs` contiene `/machines`, `/machines/{id}`, `/machines/{machineId}/parts` y `/parts/{id}` con sus métodos, y `bearerAuth` |
| REQ-36 | `MachineControllerIT` y `PartControllerIT`: un caso por par de errores del orden (`403` sobre `400`; `400` de formato sobre `404`; `404` sobre `400` de padre; `400` de padre sobre `409`) |

Tests que se tocan de la spec 01: los de `maintenance` que referencien `ConstraintViolations` (solo el paquete del `import`). Debe seguir pasando todo con `./gradlew test`.

## 8. Puntos que decidí yo y reglas que los requisitos no cubren

Decisiones de diseño a confirmar al aprobar:

1. **FK compuesta del padre** (§2.1): la misma base rechaza un padre de otra máquina. Costo: un `UNIQUE (id, machine_id)` que solo sirve de destino de la FK.
2. **`PATCH` distingue "ausente" de `null`** (§1): más código que un record plano, a cambio de cumplir REQ-25 al pie de la letra. La alternativa (tratar `null` como ausente) deja pasar en silencio un intento de mover una sub-parte a primer nivel.
3. **Id no numérico en la URL → `404`**, como los equipos de la spec 01 (REQ-41), y no `400` como el legajo: el id no tiene un formato de negocio que validar.
4. **Largo máximo de `name` (máquina y parte): 100 caracteres tras recortar → `400 VALIDATION_ERROR`.** Sin la regla, un nombre largo llega a la base y responde `500`. Es el mismo criterio de REQ-39 de la spec 01. **Requiere una enmienda a `requirements.md` (propuesta como REQ-37, sin renumerar)** antes de pasar a tareas.
5. **`ConstraintViolations` se mueve a `shared/persistence`** (§3) en lugar de duplicarse.
6. **Seed con `setval`** (§2.2): necesario porque los ids de `db.json` se cargan explícitos.
7. **Observación fuera de alcance:** CORS (`PATCH`) y el `500` de un método no soportado siguen como en la spec 01 (§2.3); no se arreglan acá salvo el permiso de `PATCH` en CORS, que esta spec sí necesita.

## 9. Trazabilidad

| REQ | Sección |
|---|---|
| 1, 2, 3 | §3 (`partCount`), §4.2, §5 |
| 4, 9 | §4.2, §5 |
| 5, 6 | §3 (`MachineCode`), §4.2 |
| 7, 10, 11 | §4.2, §6 |
| 8, 23 | §4.2, §4.3, §8 (punto 4) |
| 12, 15 | §4.2, §5 |
| 13, 14 | §4.2, §6 |
| 16, 17 | §4.3, §5 |
| 18, 19, 20 | §4.3, §5 |
| 21, 22 | §4.3, §6 |
| 24, 25, 26 | §1 (`PATCH`), §4.3 |
| 27, 28, 29 | §4.3, §6 |
| 30 | §1 (lista plana), §4.3 |
| 31, 32 | §3 (`MachinesPermissions`), §4.1 |
| 33 | §2.1, §6 |
| 34 | §2.2 |
| 35 | §1, §5 |
| 36 | §1, §4.1 |
