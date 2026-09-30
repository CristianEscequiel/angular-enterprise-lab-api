# Spec 01 — Técnicos y equipos

## Contexto y decisiones

Segunda spec del backend, módulo `maintenance`. Pasa al servidor lo que hoy
hacen `TechniciansService` y `TeamsService` del frontend sobre JSON Server
(`/tecnicos`, `/equipos`), donde la unicidad, las referencias y los permisos los
vigila el cliente (el README del frontend lo dice: "con el backend real pasan a
ser una FK y un índice único"). Fuentes: specs 013b y 013c del frontend, sus
formularios y servicios, y `db.json`. Ver `.claude/specs/ROADMAP.md`.

Decisiones tomadas para esta spec:

- **Contrato REST limpio en inglés** (ROADMAP D1): `/technicians` y `/teams`.
- **El técnico se identifica en la URL por su `legajo`** (`/technicians/{legajo}`),
  no por un id opaco: es único, no se edita y es lo que ya usa el frontend en sus
  rutas. Un id opaco fue justamente el defecto que 013a tuvo que corregir en 013c.
  La respuesta igual incluye `id` como string (ROADMAP D2). **Decisión a
  confirmar al aprobar.**
- Los equipos se identifican por `id` (string).
- Técnicos y equipos se devuelven como **colección completa, sin paginar**: el
  frontend filtra en el cliente, como hoy (ROADMAP D8).
- Sin token: `401` (spec 00). Con token pero sin permiso: `403` con el mismo
  formato `ApiError` (spec 00).
- Los conflictos de integridad responden `409` con un `code` específico dentro
  del `ApiError` de la spec 00.
- Un técnico puede ser miembro de **varios equipos** (el aviso de baja del
  frontend lista "es miembro de: A, B").
- La autorización vive en `domain`, no en el controller (regla del proyecto).

Restricciones tomadas del frontend, que se mantienen tal cual:

- Especialidad: `mecanico`, `electricista`, `general`. Tipo de equipo:
  `guardia`, `preventivo-correctivo` (`auth.model.ts`).
- Legajo: de 1 a 8 dígitos (`LEGAJO_PATTERN`).
- Permisos (`maintenance.permissions.ts`): técnicos, ver/crear/modificar →
  `administrador` y `team-leader-mantenimiento`; eliminar → `administrador`.
  Equipos, gestión completa → solo `team-leader-mantenimiento` (el administrador
  tampoco accede).

Restricción con la spec 00: la spec 00 dejó `technicians` mínima (`id`, `legajo`)
y su seed de dev (`V1_1`) ya inserta los legajos `1001` y `1002`. La migración
de esta spec tiene que convivir con esas filas.

Alcance descartado explícitamente para esta spec:

- Alta y gestión de usuarios de login: ninguna pantalla del frontend usa
  `UsersService.create`; de `users` solo se consulta si un técnico tiene login
  (para bloquear su baja). No se expone ningún endpoint de usuarios.
- El perfil del técnico dentro de la sesión (`specialty` y `teamType` en el
  login): es la enmienda 00-A, que depende de esta spec.
- Asignar órdenes a técnicos o equipos, e historial de altas y bajas de
  miembros de un equipo.
- Validar el legajo contra un sistema externo de RRHH.
- Unicidad del nombre de un equipo y coherencia entre el tipo de un equipo y el
  de sus miembros: el frontend (013c) no las valida.
- Paginación y búsqueda en el servidor.

## Requisitos

### Técnicos

### REQ-1: Listado de técnicos
CUANDO un `administrador` o un `team-leader-mantenimiento` solicita
`GET /technicians`
EL SISTEMA DEBERÁ responder `200 OK` con la colección completa de técnicos, cada
uno con `id`, `legajo`, `firstName`, `lastName`, `specialty` y `teamType`.

### REQ-2: Consulta de un técnico por legajo
CUANDO un usuario autorizado solicita `GET /technicians/{legajo}` con el legajo
de un técnico existente
EL SISTEMA DEBERÁ responder `200 OK` con ese técnico.

### REQ-3: Consulta de un técnico inexistente
CUANDO un usuario autorizado solicita `GET /technicians/{legajo}` con un legajo
que no existe
EL SISTEMA DEBERÁ responder `404 Not Found` con el `ApiError` de la spec 00.

### REQ-4: Alta de un técnico
CUANDO un usuario autorizado envía `POST /technicians` con `legajo`,
`firstName`, `lastName`, `specialty` y `teamType` válidos y un legajo no usado
EL SISTEMA DEBERÁ crear el técnico y responder `201 Created` con el técnico
creado.

### REQ-5: Alta sin usuario de login
CUANDO se crea un técnico
EL SISTEMA DEBERÁ dejarlo sin usuario de login y no crear ni modificar ninguna
fila de `users` (el técnico existe aunque todavía no pueda ingresar).

### REQ-6: Legajo duplicado
CUANDO se envía `POST /technicians` con un legajo que ya pertenece a otro técnico
EL SISTEMA DEBERÁ responder `409 Conflict` con `code` `DUPLICATE_LEGAJO` y no
crear ningún técnico.

### REQ-7: Formato del legajo
CUANDO un usuario autorizado envía `POST /technicians` con un `legajo` en el
cuerpo que no tiene entre 1 y 8 dígitos, o solicita `GET`, `PUT` o `DELETE`
`/technicians/{legajo}` con un legajo en la URL que no tiene entre 1 y 8 dígitos
(por ejemplo `abc` o `123456789`)
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y el
detalle del campo `legajo`, sin ejecutar la operación (no responde `404`).

### REQ-8: Nombre y apellido obligatorios
CUANDO se crea o se edita un técnico con `firstName` o `lastName` ausente, vacío
o compuesto solo de espacios
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y el
detalle del campo.

### REQ-9: Especialidad y tipo de equipo válidos
CUANDO se crea o se edita un técnico con una `specialty` o un `teamType` ausente
o fuera de los valores válidos
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y el
detalle del campo.

### REQ-10: Nombre y apellido se guardan recortados
CUANDO se crea o se edita un técnico con espacios al principio o al final de
`firstName` o `lastName`
EL SISTEMA DEBERÁ guardarlos sin esos espacios.

### REQ-11: Edición de un técnico
CUANDO un usuario autorizado envía `PUT /technicians/{legajo}` con `firstName`,
`lastName`, `specialty` y `teamType` válidos para un técnico existente
EL SISTEMA DEBERÁ actualizar esos cuatro campos y responder `200 OK` con el
técnico actualizado.

### REQ-12: El legajo no se edita
CUANDO el cuerpo de `PUT /technicians/{legajo}` incluye un `legajo` distinto del
de la URL
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y no
modificar el técnico.

### REQ-13: Edición de un técnico inexistente
CUANDO se envía `PUT /technicians/{legajo}` con un legajo que no existe
EL SISTEMA DEBERÁ responder `404 Not Found` y no crear ningún técnico.

### REQ-14: Baja de un técnico
CUANDO un `administrador` envía `DELETE /technicians/{legajo}` para un técnico
sin usuario de login y que no es miembro de ningún equipo
EL SISTEMA DEBERÁ eliminarlo y responder `204 No Content`.

### REQ-15: Baja bloqueada por usuario de login
CUANDO un `administrador` envía `DELETE /technicians/{legajo}` para un técnico
que tiene un usuario de login asociado
EL SISTEMA DEBERÁ responder `409 Conflict` con `code` `TECHNICIAN_IN_USE`,
indicando en el mensaje que tiene un usuario de acceso, y no eliminarlo.

### REQ-16: Baja bloqueada por pertenecer a un equipo
CUANDO un `administrador` envía `DELETE /technicians/{legajo}` para un técnico
que es miembro de uno o más equipos
EL SISTEMA DEBERÁ responder `409 Conflict` con `code` `TECHNICIAN_IN_USE`,
indicando en el mensaje los nombres de esos equipos, y no eliminarlo.

### REQ-17: Baja de un técnico inexistente
CUANDO se envía `DELETE /technicians/{legajo}` con un legajo que no existe
EL SISTEMA DEBERÁ responder `404 Not Found`.

### REQ-18: Permisos de lectura, alta y edición de técnicos
CUANDO un usuario autenticado con rol `personal-produccion` o `tecnico` solicita
`GET /technicians`, `GET /technicians/{legajo}`, `POST /technicians` o
`PUT /technicians/{legajo}`, aunque el legajo de la URL tenga formato inválido
EL SISTEMA DEBERÁ responder `403 Forbidden` y no ejecutar la operación.

### REQ-19: Permiso de baja de técnicos
CUANDO un usuario autenticado con un rol distinto de `administrador` (incluido
`team-leader-mantenimiento`) envía `DELETE /technicians/{legajo}`, aunque el legajo de la URL tenga formato
inválido
EL SISTEMA DEBERÁ responder `403 Forbidden` y no eliminar el técnico.

### Equipos

### REQ-20: Listado de equipos
CUANDO un `team-leader-mantenimiento` solicita `GET /teams`
EL SISTEMA DEBERÁ responder `200 OK` con la colección completa de equipos, cada
uno con `id`, `name`, `type` y `memberLegajos`.

### REQ-21: Consulta de un equipo
CUANDO un `team-leader-mantenimiento` solicita `GET /teams/{id}` con el id de un
equipo existente
EL SISTEMA DEBERÁ responder `200 OK` con ese equipo.

### REQ-22: Consulta de un equipo inexistente
CUANDO se solicita `GET /teams/{id}` con un id que no existe
EL SISTEMA DEBERÁ responder `404 Not Found`.

### REQ-23: Alta de un equipo
CUANDO un `team-leader-mantenimiento` envía `POST /teams` con `name`, `type` y
`memberLegajos` válidos
EL SISTEMA DEBERÁ crear el equipo y responder `201 Created` con el equipo
creado, con su `id`.

### REQ-24: Nombre del equipo obligatorio y recortado
CUANDO se crea o se edita un equipo con `name` ausente, vacío o compuesto solo
de espacios
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR`; y
CUANDO el `name` tiene espacios en los bordes, EL SISTEMA DEBERÁ guardarlo sin
ellos.

### REQ-25: Tipo de equipo válido
CUANDO se crea o se edita un equipo con un `type` ausente o fuera de `guardia` y
`preventivo-correctivo`
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR`.

### REQ-26: Miembros con formato de legajo inválido
CUANDO se envía `POST /teams` o `PUT /teams/{id}` con `memberLegajos` que
incluye un valor que no tiene entre 1 y 8 dígitos
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y no
crear ni modificar el equipo.

### REQ-27: Miembros que no existen en el maestro
CUANDO se envía `POST /teams` o `PUT /teams/{id}` con `memberLegajos` que
incluye un legajo con formato válido que no pertenece a ningún técnico
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `UNKNOWN_TECHNICIAN`,
indicando ese legajo, y no crear ni modificar el equipo.

### REQ-28: Miembros repetidos
CUANDO se envía `POST /teams` o `PUT /teams/{id}` con `memberLegajos` que
incluye el mismo legajo más de una vez
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y no
crear ni modificar el equipo.

### REQ-29: Orden de los miembros
CUANDO se consulta un equipo
EL SISTEMA DEBERÁ devolver `memberLegajos` en el mismo orden en que se guardó.

### REQ-30: Un técnico en varios equipos
CUANDO un mismo legajo se agrega como miembro a dos equipos distintos
EL SISTEMA DEBERÁ aceptarlo en ambos.

### REQ-31: Edición de un equipo
CUANDO un `team-leader-mantenimiento` envía `PUT /teams/{id}` con `name`, `type`
y `memberLegajos` válidos para un equipo existente
EL SISTEMA DEBERÁ reemplazar el nombre, el tipo y la lista completa de miembros
y responder `200 OK` con el equipo actualizado; y `memberLegajos` DEBERÁ
validarse igual que en el alta (REQ-26, REQ-27 y REQ-28): si alguna falla, el
equipo queda sin cambios (ni nombre, ni tipo, ni miembros).

### REQ-32: Edición de un equipo inexistente
CUANDO se envía `PUT /teams/{id}` con un id que no existe
EL SISTEMA DEBERÁ responder `404 Not Found` y no crear ningún equipo.

### REQ-33: Baja de un equipo
CUANDO un `team-leader-mantenimiento` envía `DELETE /teams/{id}` para un equipo
existente
EL SISTEMA DEBERÁ eliminarlo, responder `204 No Content` y dejar intactos a los
técnicos que eran sus miembros.

### REQ-34: Baja de un equipo inexistente
CUANDO se envía `DELETE /teams/{id}` con un id que no existe
EL SISTEMA DEBERÁ responder `404 Not Found`.

### REQ-35: Permisos de equipos
CUANDO un usuario autenticado con un rol distinto de
`team-leader-mantenimiento` (incluido `administrador`) solicita cualquier
operación sobre `/teams`
EL SISTEMA DEBERÁ responder `403 Forbidden` y no ejecutar la operación.

### Esquema, datos y documentación

### REQ-36: Esquema del maestro de mantenimiento
CUANDO la aplicación arranca contra una base que ya tiene las migraciones de la
spec 00
EL SISTEMA DEBERÁ aplicar una migración de Flyway que agregue a `technicians`
nombre, apellido, especialidad y tipo de equipo, y cree `teams` y la relación de
miembros, con clave foránea hacia `technicians` (sin borrado en cascada) y un
índice único por par equipo-técnico; y la migración DEBERÁ aplicarse también
sobre las filas que el seed de la spec 00 ya insertó.

### REQ-37: Datos de prueba de dev
CUANDO la aplicación arranca con el perfil `dev`
EL SISTEMA DEBERÁ dejar cargados los técnicos `1001` (Ana Ruiz, `mecanico`,
`guardia`), `1002` (Luis Paz, `electricista`, `preventivo-correctivo`) y `1003`
(Marta Gómez, `general`, `preventivo-correctivo`), y los equipos "Guardia
mecánica" (`guardia`, miembro `1001`) y "Preventivo eléctrico"
(`preventivo-correctivo`, miembro `1002`), igual que `db.json` del frontend, y
no cargarlos en ningún otro perfil; y CUANDO la base de dev ya tiene los técnicos
`1001` y `1002` sembrados por la spec 00, EL SISTEMA DEBERÁ completar sus datos
sobre esas mismas filas (conservan su `id` y queda una sola fila por legajo), de
modo que los usuarios `tecnico` y `electricista` del seed de la spec 00 sigan
asociados a `1001` y `1002`; `1003` se inserta como técnico nuevo, sin usuario de
login.

### REQ-38: Documentación OpenAPI
CUANDO se accede a la documentación de springdoc
EL SISTEMA DEBERÁ mostrar todos los endpoints de `/technicians` y `/teams`
generados desde las anotaciones del código, con el esquema de seguridad
`bearerAuth`.

### REQ-39: Largo máximo de nombres
CUANDO se crea o se edita un técnico con un `firstName` o un `lastName`, o un
equipo con un `name`, de más de 100 caracteres una vez recortado
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y el
detalle del campo, sin crear ni modificar nada.

### REQ-40: Lista de miembros obligatoria
CUANDO se envía `POST /teams` o `PUT /teams/{id}` sin `memberLegajos` (ausente o
`null`)
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y el
detalle de `memberLegajos`, sin crear ni modificar el equipo; y CUANDO
`memberLegajos` es una lista vacía, EL SISTEMA DEBERÁ aceptarla y dejar el equipo
sin miembros.

### REQ-41: Id de equipo que no es numérico
CUANDO se solicita `GET`, `PUT` o `DELETE` `/teams/{id}` con un `id` que no es un
número entero (por ejemplo `abc`)
EL SISTEMA DEBERÁ responder `404 Not Found`, igual que para un equipo que no
existe.

## Auto-revisión

- Un comportamiento por requisito: REQ-24 junta "obligatorio" y "recortado" en
  dos cláusulas CUANDO/EL SISTEMA para no duplicar el mismo caso de borde del
  campo; si se prefiere estricto, se separa en dos.
- Caminos no felices cubiertos: legajo duplicado (REQ-6), formato inválido
  (REQ-7 en cuerpo y URL, REQ-26), datos vacíos o fuera de rango (REQ-8, REQ-9, REQ-24, REQ-25),
  recursos inexistentes (REQ-3, REQ-13, REQ-17, REQ-22, REQ-32, REQ-34),
  integridad referencial (REQ-15, REQ-16, REQ-27, REQ-28) y permisos por rol
  (REQ-18, REQ-19, REQ-35). No aplica un caso de dependencia externa: el módulo
  no llama a servicios de terceros.
- Sin adjetivos no verificables: cada criterio tiene un código HTTP, un `code`
  de error o un estado concreto de la base.
- Puntos que decidí yo y conviene que confirmes:
  1. Identificar al técnico por `legajo` en la URL (contexto).
  2. Un legajo repetido o inexistente en `memberLegajos` se **rechaza** con `400`;
     el frontend hoy quita los repetidos en silencio (`uniqueMembers`) porque
     su interfaz ya los impide, pero un servidor con autoridad no debería
     corregir datos en silencio.
  3. Los `code` nuevos (`DUPLICATE_LEGAJO`, `TECHNICIAN_IN_USE`,
     `UNKNOWN_TECHNICIAN`) son propuestos, sin equivalente en el frontend.
  4. Cambiar el legajo en un `PUT` se rechaza (REQ-12) en vez de ignorarse, que
     es lo que hace el servicio del frontend.
  5. Un legajo mal formado en la URL responde `400` (no `404`), y el `403` por
     rol se evalúa antes que ese `400` (REQ-7, REQ-18, REQ-19).
  6. `PUT /teams` re-valida `memberLegajos` igual que el alta y es todo o nada
     (REQ-26 a REQ-28, REQ-31).
  7. Agregados al diseñar (REQ-39 a REQ-41): 100 caracteres como máximo para
     nombres, `memberLegajos` obligatorio (vacío permitido) y un id de equipo no
     numérico como `404`. Los valores (100, lista vacía válida) son míos.
- Riesgo para el diseño: la migración de REQ-36 agrega columnas obligatorias a
  filas ya sembradas por la spec 00 (`V1_1`). El comportamiento esperado ya está
  fijado en REQ-37 (UPDATE sobre `1001` y `1002`, INSERT de `1003`); a
  `design.md` le queda el cómo: orden de migraciones y valores de las columnas
  obligatorias sobre filas existentes.
