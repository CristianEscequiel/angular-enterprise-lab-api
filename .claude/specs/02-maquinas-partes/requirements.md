# Spec 02 — Máquinas y árbol de partes

## Contexto y decisiones

Tercera spec del backend, módulo `machines` (propio, al mismo nivel que
`maintenance`; no forma parte de él — ROADMAP y `steering/structure.md`). Pasa al servidor lo que hoy hacen
`MachinesService` y `PartsService` del frontend sobre JSON Server (`/maquinas`,
`/partes`), donde el código único, la existencia de la máquina y del padre, y el
bloqueo de bajas los verifica el cliente antes de escribir. Es la base de datos
que la spec 03 va a usar para ubicar cada orden ("qué máquina y qué parte").
Fuentes: specs 013a y 013d del frontend, sus formularios y servicios, y
`db.json`. Ver `.claude/specs/ROADMAP.md`.

Decisiones tomadas para esta spec:

- **Contrato REST limpio en inglés** (ROADMAP D1): `/machines` y `/parts`. Las
  partes de una máquina cuelgan de ella para crearlas y listarlas
  (`/machines/{machineId}/parts`) y se editan y eliminan por su id
  (`/parts/{id}`). **Rutas a confirmar al aprobar.**
- Máquinas y partes se identifican por `id` (string, ROADMAP D2). El `code` de la
  máquina es su identificador de negocio y **sí se puede editar**, por eso no es
  la clave de la URL.
- El árbol se guarda y se devuelve como **lista de adyacencia plana** (cada
  parte con su `parentId`), como hoy; armarlo como árbol sigue siendo cosa del
  cliente. Los hermanos se devuelven en **orden de creación**.
- Las colecciones se devuelven completas, sin paginar (ROADMAP D8).
- **Lectura para cualquier usuario autenticado, escritura para
  `administrador` y `team-leader-mantenimiento`.** El frontend gestiona el
  maestro solo con esos dos roles (`canManageMachines`), pero `personal-produccion`
  y `team-leader-mantenimiento` tienen que poder elegir máquina y parte al crear
  una orden (spec 03), así que el catálogo no puede ser solo de gestión.
- Sin token: `401`; sin permiso: `403`; conflictos de integridad: `409` con un
  `code` específico (`ApiError` de la spec 00).
- **Orden de errores** (REQ-36): `401 → 403 → 400 de formato → 404 del recurso
  de la URL → 400 de referencia (padre) → 409`, con la validación en `domain`
  después de autorizar, como en la spec 01.
- Sin cascada, como en el frontend: una máquina con partes o una parte con
  sub-partes no se elimina, y un subárbol se elimina de las hojas hacia arriba.

Restricciones tomadas del frontend, que se mantienen tal cual:

- `code`: se guarda sin espacios en los bordes y en mayúsculas; de 1 a 20
  caracteres de letras, dígitos y guiones, sin empezar con guion
  (`MACHINE_CODE_PATTERN`). `env-01` y `ENV-01` son la misma máquina.
- `machineId` y `parentId` de una parte no cambian después de crearla: no hay
  "mover", y por eso el árbol no puede tener ciclos ni partes cruzadas entre
  máquinas.

Alcance descartado explícitamente para esta spec:

- Asociar órdenes a máquinas y partes (spec 03), y el `breadcrumb` de la orden.
- Historial de mantenimiento, indicadores o reportes por máquina.
- Importación masiva de máquinas y partes.
- Mover una parte a otro padre o a otra máquina.
- Unicidad del nombre entre partes hermanas: el frontend no la exige (el seed
  tiene "Resistencia" bajo dos padres distintos).
- Paginación, búsqueda y orden configurable en el servidor.

## Requisitos

### Máquinas

### REQ-1: Listado de máquinas
CUANDO un usuario autenticado solicita `GET /machines`
EL SISTEMA DEBERÁ responder `200 OK` con la colección completa de máquinas, cada
una con `id`, `code`, `name` y `partCount` (la cantidad de partes que tiene).

### REQ-2: Consulta de una máquina
CUANDO un usuario autenticado solicita `GET /machines/{id}` con el id de una
máquina existente
EL SISTEMA DEBERÁ responder `200 OK` con esa máquina, con los mismos campos que
en el listado (`id`, `code`, `name` y `partCount`). Las respuestas de REQ-4
(`partCount` en `0`) y REQ-9 incluyen los mismos campos.

### REQ-3: Consulta de una máquina inexistente
CUANDO se solicita `GET /machines/{id}` con un id que no existe
EL SISTEMA DEBERÁ responder `404 Not Found` con el `ApiError` de la spec 00.

### REQ-4: Alta de una máquina
CUANDO un usuario autorizado envía `POST /machines` con `code` y `name` válidos
y un código no usado
EL SISTEMA DEBERÁ crear la máquina y responder `201 Created` con la máquina
creada, con su `id` generado por el servidor.

### REQ-5: El código se guarda normalizado
CUANDO se crea o se edita una máquina con un `code` con espacios en los bordes o
con minúsculas
EL SISTEMA DEBERÁ guardarlo sin esos espacios y en mayúsculas.

### REQ-6: Formato del código
CUANDO se crea o se edita una máquina con un `code` que, ya normalizado, no
cumple `^[A-Z0-9][A-Z0-9-]{0,19}$` (vacío, más de 20 caracteres, símbolos,
espacios internos o guion inicial)
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y el
detalle del campo `code`.

### REQ-7: Código duplicado al crear
CUANDO se envía `POST /machines` con un `code` que, normalizado, ya pertenece a
otra máquina
EL SISTEMA DEBERÁ responder `409 Conflict` con `code` `DUPLICATE_MACHINE_CODE` y
no crear ninguna máquina.

### REQ-8: Nombre de la máquina obligatorio y recortado
CUANDO se crea o se edita una máquina con `name` ausente, vacío o compuesto solo
de espacios
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR`; y
CUANDO el `name` tiene espacios en los bordes, EL SISTEMA DEBERÁ guardarlo sin
ellos.

### REQ-9: Edición de una máquina
CUANDO un usuario autorizado envía `PUT /machines/{id}` con `code` y `name`
válidos para una máquina existente
EL SISTEMA DEBERÁ actualizar ambos campos y responder `200 OK` con la máquina
actualizada, sin cambiar su `id` ni sus partes.

### REQ-10: La máquina no es duplicada de sí misma
CUANDO se envía `PUT /machines/{id}` con el mismo `code` que esa máquina ya tiene
(aunque cambie solo el nombre o solo la capitalización del código)
EL SISTEMA DEBERÁ aceptar la edición.

### REQ-11: Código duplicado al editar
CUANDO se envía `PUT /machines/{id}` con un `code` que, normalizado, pertenece a
otra máquina
EL SISTEMA DEBERÁ responder `409 Conflict` con `code` `DUPLICATE_MACHINE_CODE` y
no modificar la máquina.

### REQ-12: Edición de una máquina inexistente
CUANDO se envía `PUT /machines/{id}` con un id que no existe
EL SISTEMA DEBERÁ responder `404 Not Found` y no crear ninguna máquina.

### REQ-13: Baja de una máquina
CUANDO un usuario autorizado envía `DELETE /machines/{id}` para una máquina sin
partes
EL SISTEMA DEBERÁ eliminarla y responder `204 No Content`.

### REQ-14: Baja bloqueada por partes
CUANDO se envía `DELETE /machines/{id}` para una máquina que tiene una o más
partes
EL SISTEMA DEBERÁ responder `409 Conflict` con `code` `MACHINE_HAS_PARTS`,
indicando en el `message` la cantidad de partes que tiene, y no eliminar la máquina ni ninguna de sus
partes.

### REQ-15: Baja de una máquina inexistente
CUANDO se envía `DELETE /machines/{id}` con un id que no existe
EL SISTEMA DEBERÁ responder `404 Not Found`.

### Partes

### REQ-16: Consulta de las partes de una máquina
CUANDO un usuario autenticado solicita `GET /machines/{machineId}/parts` para
una máquina existente
EL SISTEMA DEBERÁ responder `200 OK` con todas sus partes como lista plana, cada
una con `id`, `machineId`, `parentId` (`null` en las de primer nivel) y `name`,
en orden de creación.

### REQ-17: Consulta de las partes de una máquina inexistente
CUANDO se solicita `GET /machines/{machineId}/parts` con una máquina que no
existe
EL SISTEMA DEBERÁ responder `404 Not Found`.

### REQ-18: Alta de una parte de primer nivel
CUANDO un usuario autorizado envía `POST /machines/{machineId}/parts` con `name`
válido y `parentId` nulo o ausente para una máquina existente
EL SISTEMA DEBERÁ crear la parte con `parentId` en `null` y responder
`201 Created` con la parte creada.

### REQ-19: Alta de una sub-parte
CUANDO un usuario autorizado envía `POST /machines/{machineId}/parts` con `name`
válido y un `parentId` que corresponde a una parte de esa misma máquina
EL SISTEMA DEBERÁ crear la sub-parte y responder `201 Created` con la parte
creada.

### REQ-20: Alta en una máquina inexistente
CUANDO se envía `POST /machines/{machineId}/parts` con una máquina que no existe
EL SISTEMA DEBERÁ responder `404 Not Found` y no crear ninguna parte.

### REQ-21: Padre inexistente
CUANDO se envía `POST /machines/{machineId}/parts` para una máquina existente con
un `parentId` que no corresponde a ninguna parte
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code`
`PARENT_PART_NOT_FOUND` y no crear la parte.

### REQ-22: Padre de otra máquina
CUANDO se envía `POST /machines/{machineId}/parts` para una máquina existente con
un `parentId` que corresponde a una parte de otra máquina
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code`
`PARENT_PART_OTHER_MACHINE` y no crear la parte.

### REQ-23: Nombre de la parte obligatorio y recortado
CUANDO se crea o se edita una parte con `name` ausente, vacío o compuesto solo
de espacios
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR`; y
CUANDO el `name` tiene espacios en los bordes, EL SISTEMA DEBERÁ guardarlo sin
ellos.

### REQ-24: Edición del nombre de una parte
CUANDO un usuario autorizado envía `PATCH /parts/{id}` con un `name` válido para
una parte existente
EL SISTEMA DEBERÁ cambiar solo el nombre y responder `200 OK` con la parte
actualizada, conservando su `machineId`, su `parentId` y su posición en el
árbol.

### REQ-25: La parte no se mueve
CUANDO el cuerpo de `PATCH /parts/{id}`, para una parte existente, incluye un `machineId` o un `parentId`
distinto del actual
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y no
modificar la parte.

### REQ-26: Edición de una parte inexistente
CUANDO se envía `PATCH /parts/{id}` con un id que no existe
EL SISTEMA DEBERÁ responder `404 Not Found`.

### REQ-27: Baja de una parte
CUANDO un usuario autorizado envía `DELETE /parts/{id}` para una parte sin
sub-partes
EL SISTEMA DEBERÁ eliminarla y responder `204 No Content`.

### REQ-28: Baja bloqueada por sub-partes
CUANDO se envía `DELETE /parts/{id}` para una parte que tiene una o más
sub-partes
EL SISTEMA DEBERÁ responder `409 Conflict` con `code` `PART_HAS_CHILDREN`,
indicando en el `message` la cantidad de sub-partes que tiene, y no eliminar la parte ni ninguna de sus
sub-partes.

### REQ-29: Baja de una parte inexistente
CUANDO se envía `DELETE /parts/{id}` con un id que no existe
EL SISTEMA DEBERÁ responder `404 Not Found`.

### REQ-30: Profundidad variable
CUANDO se crea en una máquina un árbol de al menos cinco niveles y se consulta
`GET /machines/{machineId}/parts`
EL SISTEMA DEBERÁ devolver todas las partes, cada una con su `parentId` original,
de modo que reconstruir el árbol con esos datos reproduzca la jerarquía creada
sin perder ni aplanar ningún nivel.

### Permisos

### REQ-31: Permisos de escritura
CUANDO un usuario autenticado con rol `personal-produccion` o `tecnico` envía
`POST`, `PUT`, `PATCH` o `DELETE` sobre `/machines` o `/parts`
EL SISTEMA DEBERÁ responder `403 Forbidden` y no ejecutar la operación.

### REQ-32: Permisos de lectura
CUANDO un usuario autenticado, con cualquiera de los cuatro roles, solicita
`GET /machines`, `GET /machines/{id}` o `GET /machines/{machineId}/parts`
EL SISTEMA DEBERÁ responder con los datos y no con `403`.

### REQ-36: Orden de los errores
CUANDO una petición a `/machines` o `/parts` reúne más de una causa de error
EL SISTEMA DEBERÁ responder el primero de este orden: `401` (sin token), `403`
(sin permiso), `400` de formato (`name`, `code`), `404` del recurso de la URL
(máquina o parte), `400` de referencia (`PARENT_PART_NOT_FOUND`,
`PARENT_PART_OTHER_MACHINE`, intento de mover en REQ-25) y `409`.

### Esquema, datos y documentación

### REQ-33: Integridad del árbol en la base de datos
CUANDO la aplicación arranca contra una base que ya tiene las migraciones hasta
la enmienda 00-A (`V5`)
EL SISTEMA DEBERÁ aplicar una migración de Flyway `V6` (el seed de dev de REQ-34,
`V6_1`) que cree `machines` (con
índice único sobre `code`) y `parts`, con clave foránea de cada parte hacia su
máquina y hacia su padre, sin borrado en cascada, de modo que la base rechace por
sí misma una parte con máquina o padre inexistente y el borrado de una máquina o
de una parte con dependientes.

### REQ-34: Datos de prueba de dev
CUANDO la aplicación arranca con el perfil `dev`
EL SISTEMA DEBERÁ dejar cargadas las máquinas `ENV-01` "Envasadora línea 1",
`SEL-02` "Selladora" y `ROT-03` "Rotuladora", y las diez partes de `db.json`
(la Envasadora con su árbol de cuatro niveles y una hoja hermana en el nivel 2,
la Selladora con dos niveles y la Rotuladora sin partes), **conservando los ids
de `db.json`** (máquinas `1` a `3`, partes `1` a `10`) porque las órdenes de
prueba de la spec 03 los referencian, con los ids nuevos continuando después del
mayor, y no cargarlas en ningún otro perfil.

### REQ-35: Documentación OpenAPI
CUANDO se accede a la documentación de springdoc
EL SISTEMA DEBERÁ mostrar todos los endpoints de `/machines` y `/parts`
generados desde las anotaciones del código, con el esquema de seguridad
`bearerAuth`.

### REQ-37: Largo máximo del nombre
CUANDO se crea o se edita una máquina o una parte con un `name` de más de 100
caracteres una vez recortado
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y el
detalle del campo `name`, y no guardarlo; un `name` de exactamente 100 caracteres
se acepta. (Enmienda surgida en `design.md` §8: sin la regla, un nombre largo
llegaría a la base y respondería `500`.)

## Auto-revisión

- Un comportamiento por requisito: REQ-8 y REQ-23 juntan "obligatorio" y
  "recortado" en dos cláusulas para no repetir el mismo campo; si se prefiere
  estricto, se separan. REQ-31 cubre cuatro verbos porque es una sola regla
  (rol sin permiso de escritura), igual que REQ-11 de la spec 00.
- Caminos no felices cubiertos: formato y vacío (REQ-6, REQ-8, REQ-23),
  duplicados (REQ-7, REQ-11), inexistentes (REQ-3, REQ-12, REQ-15, REQ-17,
  REQ-20, REQ-26, REQ-29), padre inválido (REQ-21, REQ-22), intento de mover una
  parte (REQ-25), bajas con dependientes (REQ-14, REQ-28) y permisos (REQ-31).
  No aplica dependencia externa.
- Sin adjetivos no verificables: cada criterio fija un código HTTP, un `code` de
  error o un estado de la base.
- Puntos que decidí yo y conviene que confirmes:
  1. Rutas anidadas para listar y crear partes (contexto).
  2. **Lectura abierta a los cuatro roles**, aunque el frontend hoy solo deja
     entrar a la gestión a administrador y team leader: sin eso `personal-produccion`
     no podría elegir máquina y parte al crear una orden.
  3. `partCount` en el listado de máquinas (REQ-1): el frontend lo calcula
     bajando todas las partes; con el servidor conviene que ya venga.
  4. Los `code` nuevos (`DUPLICATE_MACHINE_CODE`, `MACHINE_HAS_PARTS`,
     `PART_HAS_CHILDREN`, `PARENT_PART_NOT_FOUND`, `PARENT_PART_OTHER_MACHINE`)
     son propuestos.
  5. `PATCH` con `machineId` o `parentId` distintos se rechaza (REQ-25); el
     frontend directamente no los envía.
- Correcciones aplicadas tras la primera lectura: migraciones desde `V6`
  (REQ-33), `machines` como módulo propio, orden de errores (REQ-36, con REQ-21,
  REQ-22 y REQ-25 acotados a recurso existente), cantidad en el `message` de los
  `409` (REQ-14, REQ-28) y `partCount` también en la consulta individual (REQ-2).
- Fuera de los requisitos, para el diseño: ya no hace falta detectar partes
  huérfanas ni ciclos como hace `buildPartTree` en el cliente, porque REQ-22,
  REQ-25 y REQ-33 los vuelven imposibles a nivel de base y de dominio; el
  cliente puede conservar esa defensa sin costo.
