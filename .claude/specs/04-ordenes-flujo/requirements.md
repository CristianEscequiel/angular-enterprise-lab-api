# Spec 04 — Órdenes de trabajo: tomar, cerrar y liberar

## Contexto y decisiones

Quinta spec del backend, módulo `workorders`. Pasa al servidor el flujo de vida
de una orden que hoy resuelve `WorkOrdersService.take`, `close` y `release` en
el cliente: lee la orden "fresca", escribe si se cumple la condición y vuelve a
leer, con una ventana entre lectura y escritura que el README del frontend
reconoce ("es aceptable en el mock y el backend real lo cierra con una
actualización condicional"). Esa actualización condicional es el centro de esta
spec. Fuentes: ampliación de la spec 013d del frontend, `work-order.service.ts`,
`work-order.permissions.ts`, las páginas de listado y de cierre. Ver
`.claude/specs/ROADMAP.md`.

Decisiones tomadas para esta spec:

- **Acciones dedicadas** (ROADMAP D4): `POST /work-orders/{id}/take`,
  `POST /work-orders/{id}/close` y `POST /work-orders/{id}/release`. Son la única
  vía para cambiar el estado, el dueño o la nota de cierre; el `PUT` de la spec
  03 no los toca.
- **La identidad sale del token**, no del cuerpo. El dueño es el usuario
  autenticado: `takenBy.id` es su id (string), `takenBy.name` y
  `closingNote.authorName` son una foto de su `displayName` (requiere la
  enmienda 00-A) y `at` lo pone el servidor en UTC. El cliente no manda quién
  toma ni quién cierra.
- **El tipo de equipo del técnico se lee del maestro de técnicos** por el legajo
  de su usuario (spec 01), nunca de datos que mande el cliente ni del token.
- Máquina de estados: `pending` → `in-progress` (tomar) → `completed` o
  `cancelled` (cerrar); `in-progress` → `pending` (liberar). Una orden cerrada no
  se reabre.
- **Conflictos de estado: `409 Conflict`** con `details` en el `ApiError` de la
  spec 00: `status` (el estado real de la orden) y, si tiene dueño, `takenById` y
  `takenByName`. Es lo que necesita la pantalla para decir "la está ejecutando X"
  (`WorkOrderStateError`). Códigos propuestos: `WORK_ORDER_NOT_PENDING`,
  `WORK_ORDER_NOT_IN_PROGRESS` y `WORK_ORDER_TAKEN_BY_OTHER`.
- **Atomicidad**: cada transición es una actualización condicional sobre el
  estado y el dueño esperados, no una lectura seguida de una escritura.
- La autorización de rol se evalúa **antes** que el estado de la orden, así un
  rol sin permiso recibe `403` y no se entera del estado.
- Un técnico puede tener varias órdenes en progreso: el frontend no lo limita.
- La autorización vive en `domain`.

Restricciones tomadas del frontend, que se mantienen tal cual:

- Tomar: solo el técnico cuyo tipo de equipo atiende el tipo de la orden
  (`guardia` → `pronto-intervencion`; `preventivo-correctivo` → `preventivo` y
  `correctivo`).
- Cerrar: solo quien la tomó, con resultado `completed` o `cancelled` (sin valor
  por defecto) y un comentario de 50 a 500 caracteres sin contar los espacios de
  los bordes.
- Liberar: `administrador` y `team-leader-mantenimiento`, sobre una orden en
  progreso; vuelve a `pending` sin dueño y sin dejar historial de quién la
  liberó.
- La especialidad del técnico **no** se evalúa: las órdenes todavía no la llevan.

Alcance descartado explícitamente para esta spec:

- Que administrador o team leader cierren órdenes, y que el técnico devuelva su
  propia orden.
- Registrar quién liberó una orden (auditoría) y reabrir una orden cerrada.
- Historial de varios comentarios y comentarios mientras la orden está en
  progreso.
- Asignar una orden a un técnico concreto (pendiente también en el frontend).
- Limitar la cantidad de órdenes en progreso por técnico.

## Requisitos

### Tomar una orden

### REQ-1: Tomar una orden pendiente
CUANDO un técnico habilitado envía `POST /work-orders/{id}/take` para una orden
`pending`
EL SISTEMA DEBERÁ responder `200 OK` con la orden en `status` `in-progress` y
`takenBy` con el id del usuario, su nombre y la hora del servidor.

### REQ-2: Quién toma sale del token
CUANDO el cuerpo de `POST /work-orders/{id}/take` incluye un `takenBy` o un
identificador de otro usuario
EL SISTEMA DEBERÁ ignorarlo y dejar la orden a nombre del usuario autenticado.

### REQ-3: Habilitación por tipo de equipo
CUANDO un técnico con tipo de equipo `guardia` toma una orden `preventivo` o
`correctivo`, o uno con `preventivo-correctivo` toma una `pronto-intervencion`
EL SISTEMA DEBERÁ responder `403 Forbidden` y no modificar la orden; y CUANDO el
tipo de equipo coincide, EL SISTEMA DEBERÁ permitir tomarla.

### REQ-4: Tomar sin ser técnico
CUANDO un usuario con rol `administrador`, `team-leader-mantenimiento` o
`personal-produccion` envía `POST /work-orders/{id}/take`
EL SISTEMA DEBERÁ responder `403 Forbidden` y no modificar la orden.

### REQ-5: Tomar una orden inexistente
CUANDO se envía `POST /work-orders/{id}/take` con un id que no existe
EL SISTEMA DEBERÁ responder `404 Not Found`.

### REQ-6: Tomar una orden que ya no está pendiente
CUANDO se envía `POST /work-orders/{id}/take` para una orden `in-progress`
(propia o de otro técnico), `completed` o `cancelled`
EL SISTEMA DEBERÁ responder `409 Conflict` con `code`
`WORK_ORDER_NOT_PENDING` y `details` con su `status` real y, si tiene dueño,
`takenById` y `takenByName`, sin modificar la orden.

### REQ-7: Dos técnicos toman a la vez
CUANDO dos técnicos habilitados envían `take` para la misma orden `pending` de
forma simultánea
EL SISTEMA DEBERÁ dejarla a nombre de uno solo, responder `200 OK` a ese y
`409 Conflict` al otro con el dueño real en `details`.

### REQ-8: Varias órdenes en progreso por técnico
CUANDO un técnico que ya tiene una orden `in-progress` toma otra orden
habilitada y pendiente
EL SISTEMA DEBERÁ aceptarlo y dejar ambas a su nombre.

### Cerrar una orden

### REQ-9: Cerrar una orden propia
CUANDO el técnico dueño de una orden `in-progress` envía
`POST /work-orders/{id}/close` con `outcome` `completed` o `cancelled` y un
`comment` válido
EL SISTEMA DEBERÁ responder `200 OK` con la orden en `status` igual al `outcome`,
conservando `takenBy`, y con una `closingNote` con el comentario recortado, el id
y el nombre del usuario como autor, y la hora del servidor.

### REQ-10: El autor sale del token
CUANDO el cuerpo de `POST /work-orders/{id}/close` incluye un autor o un nombre de
autor
EL SISTEMA DEBERÁ ignorarlos y registrar como autor al usuario autenticado.

### REQ-11: Resultado obligatorio
CUANDO se envía `close` con `outcome` ausente o distinto de `completed` y
`cancelled` (incluidos `pending` e `in-progress`)
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y el
detalle del campo, sin modificar la orden.

### REQ-12: Comentario obligatorio
CUANDO se envía `close` con `comment` ausente, vacío o compuesto solo de espacios
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y no
modificar la orden.

### REQ-13: Comentario demasiado corto
CUANDO el `comment` tiene menos de 50 caracteres sin contar los espacios de los
bordes (por ejemplo 49, o 10 letras rodeadas de 60 espacios)
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y no
modificar la orden; y CUANDO tiene exactamente 50, EL SISTEMA DEBERÁ aceptarlo.

### REQ-14: Comentario demasiado largo
CUANDO el `comment` tiene más de 500 caracteres sin contar los espacios de los
bordes
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y no
modificar la orden; y CUANDO tiene exactamente 500, EL SISTEMA DEBERÁ aceptarlo.

### REQ-15: Cerrar sin ser el técnico habilitado
CUANDO un usuario con rol distinto de `tecnico`, o un técnico cuyo tipo de equipo
no atiende el tipo de la orden, envía `close`
EL SISTEMA DEBERÁ responder `403 Forbidden` y no modificar la orden.

### REQ-16: Cerrar una orden inexistente
CUANDO se envía `POST /work-orders/{id}/close` con un id que no existe
EL SISTEMA DEBERÁ responder `404 Not Found`.

### REQ-17: Cerrar una orden que no está en progreso
CUANDO se envía `close` para una orden `pending`, `completed` o `cancelled`
EL SISTEMA DEBERÁ responder `409 Conflict` con `code`
`WORK_ORDER_NOT_IN_PROGRESS` y `details` con su `status` real, sin modificar la
orden.

### REQ-18: Cerrar la orden de otro técnico
CUANDO un técnico habilitado envía `close` para una orden `in-progress` cuyo
dueño es otro técnico
EL SISTEMA DEBERÁ responder `409 Conflict` con `code`
`WORK_ORDER_TAKEN_BY_OTHER` y `details` con `takenById` y `takenByName`, sin
modificar la orden.

### REQ-19: Una orden cerrada no se reabre
CUANDO se envía `close`, `take` o `release` para una orden `completed` o
`cancelled`
EL SISTEMA DEBERÁ rechazarlo con `409 Conflict` y conservar su estado, su dueño
y su nota de cierre sin cambios.

### REQ-20: Cierre duplicado
CUANDO el mismo técnico envía `close` dos veces para la misma orden (por ejemplo
por un doble envío)
EL SISTEMA DEBERÁ aplicar el primero, responder `409 Conflict` con `code`
`WORK_ORDER_NOT_IN_PROGRESS` al segundo y no sobrescribir la `closingNote`.

### REQ-21: Cierre contra liberación
CUANDO el dueño intenta cerrar una orden que en ese momento se libera, o que ya
fue liberada
EL SISTEMA DEBERÁ aplicar solo una de las dos operaciones, rechazar la otra con
`409 Conflict` y nunca dejar el cierre del técnico anterior aplicado sobre una
orden liberada.

### Liberar una orden

### REQ-22: Liberar una orden en progreso
CUANDO un `administrador` o un `team-leader-mantenimiento` envía
`POST /work-orders/{id}/release` para una orden `in-progress`
EL SISTEMA DEBERÁ responder `200 OK` con la orden en `status` `pending`, con
`takenBy` en `null` y sin `closingNote`.

### REQ-23: Liberar sin permiso
CUANDO un `tecnico` (incluido el dueño de la orden) o un `personal-produccion`
envía `release`
EL SISTEMA DEBERÁ responder `403 Forbidden` y no modificar la orden.

### REQ-24: Liberar una orden que no está en progreso
CUANDO se envía `release` para una orden `pending`, `completed` o `cancelled`
EL SISTEMA DEBERÁ responder `409 Conflict` con `code`
`WORK_ORDER_NOT_IN_PROGRESS` y `details` con su `status` real, sin modificar la
orden.

### REQ-25: Liberar una orden inexistente
CUANDO se envía `POST /work-orders/{id}/release` con un id que no existe
EL SISTEMA DEBERÁ responder `404 Not Found`.

### REQ-26: Una orden liberada se puede volver a tomar
CUANDO una orden liberada vuelve a estar `pending`
EL SISTEMA DEBERÁ permitir que cualquier técnico habilitado la tome, incluido el
que la tenía antes.

### Permisos y coherencia

### REQ-27: El permiso se evalúa antes que el estado
CUANDO un usuario cuyo rol no tiene permiso para una acción la envía sobre una
orden que además no está en el estado esperado
EL SISTEMA DEBERÁ responder `403 Forbidden` y no `409`.

### REQ-28: Solo estas acciones cambian el estado
CUANDO se envía `PUT /work-orders/{id}` con un `status`, un `takenBy` o una
`closingNote`
EL SISTEMA DEBERÁ conservar los actuales (REQ-35 de la spec 03), de modo que
tomar, cerrar y liberar sean la única vía para cambiarlos.

### REQ-29: El listado refleja las transiciones
CUANDO una orden cambia de estado por `take`, `close` o `release`
EL SISTEMA DEBERÁ devolverla con el nuevo `status`, `takenBy` y `closingNote` en
la siguiente consulta de `GET /work-orders`, `GET /work-orders/{id}` y en el
filtro `status`.

### REQ-30: Invariantes de estado en la base de datos
CUANDO la aplicación arranca contra una base que ya tiene las migraciones de la
spec 03
EL SISTEMA DEBERÁ aplicar una migración de Flyway con restricciones que rechacen
por sí mismas una orden `pending` con dueño o con nota de cierre, una
`in-progress` sin dueño o con nota de cierre, y una `completed` o `cancelled` sin
dueño, sin nota de cierre o con un autor distinto del dueño; y la migración
DEBERÁ aplicarse sobre las órdenes de prueba de la spec 03 sin modificarlas.
_Enmienda 04-A: en lo que dice de la `cancelled` sin dueño, lo reemplazan REQ-41 y
REQ-47._

### REQ-31: Documentación OpenAPI
CUANDO se accede a la documentación de springdoc
EL SISTEMA DEBERÁ mostrar los endpoints `take`, `close` y `release`, con sus
respuestas `403`, `404` y `409`, generados desde las anotaciones del código.

## Auto-revisión

- Un comportamiento por requisito: REQ-3 y REQ-13 y REQ-14 llevan dos cláusulas
  (rechazo y caso límite aceptado) porque los límites de 49/50 y 500/501
  caracteres son un solo criterio de la 013d ("49 → bloqueado; 50 → se envía");
  REQ-19 agrupa los tres verbos porque la regla es una: lo cerrado no se toca.
- Caminos no felices cubiertos: rol sin permiso (REQ-3, REQ-4, REQ-15, REQ-23),
  inexistentes (REQ-5, REQ-16, REQ-25), estado incorrecto (REQ-6, REQ-17,
  REQ-24), dueño distinto (REQ-18), datos inválidos (REQ-11 a REQ-14) y
  concurrencia (REQ-7, REQ-20, REQ-21). No aplica una dependencia externa.
- Sin adjetivos no verificables: cada criterio fija un código HTTP, un `code`,
  un `details` o un estado concreto de los datos. Los criterios de concurrencia
  (REQ-7, REQ-20, REQ-21) se prueban con hilos que disparan las dos operaciones a
  la vez.
- Puntos que decidí yo y conviene que confirmes:
  1. Acciones dedicadas en vez de `PATCH` (ROADMAP D4): el frontend cambia solo
     `take`, `close` y `release` de `WorkOrdersService`.
  2. **Tomar una orden propia que ya está en progreso responde `409`** (REQ-6)
     con el dueño en `details`; hoy el frontend decide "continuar" con datos
     locales y, si la lista estaba vieja, resuelve el error mirando `takenBy.id`
     (`handleTakeError`). No lo hice idempotente para no esconder que la lista
     estaba desactualizada.
  3. El formato de `details` (`status`, `takenById`, `takenByName`) y los tres
     `code` de `409` son propuestos: reutilizan el campo `details` de `ApiError`,
     que hoy solo llevan los errores de validación.
  4. Un técnico puede tener varias órdenes en progreso (REQ-8), como hoy.
- Dependencias: la spec 01 (tipo de equipo del técnico) y la enmienda 00-A
  (`displayName`, id de usuario y perfil del técnico) tienen que estar
  implementadas antes; la spec 03 aporta `work_orders`, `takenBy` y
  `closingNote`.
- Riesgo para el diseño: REQ-30 agrega restricciones sobre filas ya sembradas por
  la spec 03; las órdenes de `db.json` cumplen esos invariantes (los verifica
  `db.seed.spec.ts` del frontend), pero el mapeo de dueños por usuario (REQ-44 de
  la spec 03) tiene que quedar consistente con `takenBy.id` y con el autor de la
  nota.

---

# Enmienda 04-A — Cancelar una orden pendiente

## Contexto y decisiones (enmienda)

El frontend (`angular-enterprise-lab`, spec 019, rama `feat/019-cancelar-orden`) ya
consume `POST /work-orders/{id}/cancel` y el endpoint no existe. Hoy una orden pasa
a `cancelled` solo por `close` con `outcome` `cancelled` (técnico dueño, con
comentario de 50 a 500). Esta enmienda suma **una transición nueva** a la máquina de
estados: `pending` → `cancelled`, sin pasar por `in-progress`. REQ-1 a REQ-31 no
cambian, salvo lo que dice REQ-41 sobre los invariantes de REQ-30.

Decisiones tomadas:

- **Acción dedicada** `POST /work-orders/{id}/cancel`, sin cuerpo, `200` con el
  `WorkOrderResponse` de `take`, `release` y `close`.
- **Roles:** `administrador` y `team-leader-mantenimiento` (el mismo mecanismo que
  `release` y `PUT`). `tecnico` y `personal-produccion`, `403`. La autoridad es del
  servidor; el cliente solo oculta el botón.
- **Solo desde `pending`.** Una `in-progress` se libera primero (`release`);
  `completed` y `cancelled` no se cancelan. El `409` reutiliza el código
  `WORK_ORDER_NOT_PENDING` de `take` y el `details` de la spec 04 (`status` y, si
  hay dueño, `takenById` y `takenByName`).
- **Sin comentario ni motivo** (decisión del usuario para esta spec).
- **Una cancelación hecha por este endpoint no tiene `closingNote` en la
  respuesta.** La `closingNote` del contrato siempre lleva un comentario de 50 a
  500 caracteres y esta cancelación no lo tiene; devolverla con el comentario nulo
  rompería ese contrato. Quién canceló y cuándo se **guarda** en las columnas
  `closing_author_id`, `closing_author_name` y `closed_at` (auditoría y dashboard),
  pero la API no lo expone en esta enmienda.
- **Cambio de esquema (decisión del usuario):** migración `V9` que relaja los
  `CHECK` de `V8` para que una `cancelled` pueda no tener dueño ni comentario, pero
  exija quién canceló y cuándo (`closing_author_*` y `closed_at`).
- **Dashboard:** `DashboardStatisticsAdapter` cuenta `cancelled` por `closed_at` y
  calcula el promedio solo sobre `completed`; con `closed_at` informado, la
  cancelación entra en `closedInPeriod` sin tocar el dashboard (REQ-45 lo fija con
  una prueba).
- **Se mantiene** el flujo de cierre del técnico (`close` con `cancelled`), la
  atomicidad por actualización condicional y la autorización en `domain`.

Alcance descartado explícitamente en esta enmienda:

- Cancelar órdenes `in-progress`, o que un técnico cancele por este endpoint.
- Pedir o guardar un motivo; exponer quién canceló en la respuesta; reabrir una
  orden cancelada.
- Cambiar el contrato de `close`, `take` o `release`, o el `PUT`.

## Requisitos (enmienda)

### Cancelar una orden

### REQ-32: Cancelar una orden pendiente
CUANDO un `administrador` o un `team-leader-mantenimiento` envía
`POST /work-orders/{id}/cancel` para una orden `pending`
EL SISTEMA DEBERÁ responder `200 OK` con la orden en `status` `cancelled`, con
`takenBy` en `null`, sin `closingNote` y con los demás campos (título, descripción,
tipo, prioridad, máquina, `createdAt`) sin cambios.

### REQ-33: El cuerpo se ignora
CUANDO `POST /work-orders/{id}/cancel` incluye un cuerpo (por ejemplo `status`,
`takenBy`, `closingNote` o un `comment`), sea JSON válido, malformado o de otro
`Content-Type`
EL SISTEMA DEBERÁ ignorarlo y aplicar solo la cancelación.

### REQ-34: Cancelar sin permiso
CUANDO un usuario con rol `tecnico` (incluido el dueño de la orden) o
`personal-produccion` envía `cancel`
EL SISTEMA DEBERÁ responder `403 Forbidden` y no modificar la orden.

### REQ-35: Cancelar sin autenticación
CUANDO se envía `POST /work-orders/{id}/cancel` sin token, o con uno inválido
EL SISTEMA DEBERÁ responder `401 Unauthorized` en el `ApiError` de la spec 00 y no
modificar la orden.

### REQ-36: Cancelar una orden inexistente
CUANDO se envía `POST /work-orders/{id}/cancel` con un id que no existe o que no es
numérico
EL SISTEMA DEBERÁ responder `404 Not Found`.

### REQ-37: Cancelar una orden que no está pendiente
CUANDO un `administrador` o un `team-leader-mantenimiento` envía `cancel` para una
orden `in-progress`, `completed` o `cancelled`
EL SISTEMA DEBERÁ responder `409 Conflict` con `code` `WORK_ORDER_NOT_PENDING` y
`details` con su `status` real y, si tiene dueño, `takenById` y `takenByName`, sin
modificar la orden.

### REQ-38: El permiso se evalúa antes que el estado
CUANDO un `tecnico` o un `personal-produccion` envía `cancel` sobre una orden que
además no está `pending`, o sobre un id que no existe
EL SISTEMA DEBERÁ responder `403 Forbidden` y no `409` ni `404`.

### REQ-39: Una cancelación no se repite ni se retoma
CUANDO se envía `cancel` o `take` para una orden que ya fue cancelada por este
endpoint
EL SISTEMA DEBERÁ responder `409 Conflict` con `code` `WORK_ORDER_NOT_PENDING` y
`details.status` `cancelled`, sin `takenById` (no tiene dueño), y conservar la
orden sin cambios.

### REQ-48: Una cancelación no se cierra ni se libera
CUANDO se envía `close` o `release` para una orden que ya fue cancelada por
`cancel` (sin dueño)
EL SISTEMA DEBERÁ responder `409 Conflict` con `code` `WORK_ORDER_NOT_IN_PROGRESS`
(en `close`, no `WORK_ORDER_TAKEN_BY_OTHER`) y conservar la orden sin cambios.
_Extiende REQ-19 a la cancelación sin dueño._

### REQ-40: Cancelación contra toma
CUANDO un `cancel` y un `take` llegan a la vez para la misma orden `pending`, o dos
`cancel` a la vez
EL SISTEMA DEBERÁ aplicar una sola operación, responder `200 OK` a esa y
`409 Conflict` a la otra con el estado real en `details`, y nunca dejar una orden
`cancelled` con dueño ni `in-progress` marcada como cancelada.

### Datos y coherencia

### REQ-41: Invariantes en la base de datos
CUANDO la aplicación arranca contra una base con las migraciones hasta `V8`
EL SISTEMA DEBERÁ aplicar una migración de Flyway `V9` que reemplace los `CHECK`
de `V8` (`work_orders_closing_group_check`, que hoy exige `closing_comment` junto
con el autor, y `work_orders_state_invariants_check`, que hoy exige dueño para
`cancelled`) y acepte una orden `cancelled` sin dueño y sin comentario, siempre
que tenga `closing_author_id`, `closing_author_name` y `closed_at`; y la migración
DEBERÁ aplicarse sobre las órdenes de prueba sin modificarlas.
_Reemplaza parcialmente a REQ-30 (spec 04): solo en lo que REQ-30 decía de la
`cancelled` sin dueño._

### REQ-47: Lo que la base sigue rechazando
CUANDO se intenta guardar por SQL una orden en alguna de estas combinaciones
EL SISTEMA DEBERÁ rechazarla con un `CHECK`: `pending` o `in-progress` con nota de
cierre; `completed` sin dueño, sin comentario o con autor distinto del dueño;
`cancelled` con dueño pero sin comentario o con autor distinto del dueño;
`cancelled` sin dueño y con comentario; `cancelled` sin dueño y sin
`closing_author_id`, `closing_author_name` o `closed_at`.

### REQ-42: Quién y cuándo se guardan, sin exponerse
CUANDO una orden se cancela por `cancel`
EL SISTEMA DEBERÁ guardar en `closing_author_id` y `closing_author_name` al usuario
autenticado (id y `displayName` en ese momento) y en `closed_at` la hora del
servidor en UTC, y la respuesta de `GET /work-orders/{id}` y del listado DEBERÁ
seguir sin `closingNote`.

### REQ-43: El cierre del técnico no cambia
CUANDO el técnico dueño envía `close` con `outcome` `cancelled`
EL SISTEMA DEBERÁ exigir el comentario de 50 a 500 caracteres y devolver la
`closingNote` completa, igual que en REQ-9 a REQ-14.

### REQ-44: La consulta refleja la cancelación
CUANDO una orden se cancela por `cancel`
EL SISTEMA DEBERÁ devolverla con `status` `cancelled` y `takenBy` en `null` en
`GET /work-orders/{id}`, en `GET /work-orders` y en el filtro `status=cancelled`.

### REQ-45: El dashboard cuenta la cancelación
CUANDO una orden se cancela por `cancel` y su `closed_at` cae dentro del período
consultado
EL SISTEMA DEBERÁ incluirla en `closedInPeriod.cancelled` y en `closedInPeriod.total`
de `GET /dashboard/summary`, y en el conteo por estado `cancelled`, sin cambiar
`averageResolutionMinutes` ni `GET /dashboard/workload`, y sin que el resumen
falle por la ausencia de dueño o de comentario.

### REQ-46: Documentación OpenAPI
CUANDO se accede a la documentación de springdoc
EL SISTEMA DEBERÁ mostrar `POST /work-orders/{id}/cancel` con `bearerAuth` y sus
respuestas `200`, `403`, `404` y `409`, generada desde las anotaciones del código.

## Auto-revisión (enmienda)

- Un comportamiento por requisito. REQ-40 agrupa toma y cancelación porque la regla
  es una (un solo ganador). REQ-44 extiende REQ-29 y REQ-48 extiende REQ-19 a la
  cancelación.
- Para el diseño: `WorkOrderMapper.toDomain` arma hoy la `ClosingNote` cuando
  `closing_author_id` no es nulo; con `cancel` eso daría una `closingNote` con
  comentario nulo. El mapper tiene que decidirlo por `closing_comment` (REQ-32, 42 y
  44 se prueban en la respuesta de `cancel`, en `GET` y en el listado).
- Caminos no felices: rol sin permiso (REQ-34), sin token (REQ-35), inexistente
  (REQ-36), estado incorrecto (REQ-37, 39), concurrencia (REQ-40). Orden de errores:
  `401 → 403 → 404 → 409`; no hay `400` porque no hay cuerpo.
- Puntos que decidí yo y conviene que confirmes:
  1. **Sin `closingNote` en la respuesta** de una cancelación hecha por `cancel`
     (REQ-32, 42); el autor se guarda pero no se expone.
  2. **Se guarda quién canceló** aunque el pedido no lo exigía, porque `V9` mantiene
     el grupo `closing_author_*`/`closed_at` completo y el dashboard filtra por
     `closed_at`.
  3. **`409` para `cancel` sobre una orden ya cancelada** (REQ-39), no idempotente,
     igual que `take` sobre una propia (punto 2 de la auto-revisión de la spec).
  4. **REQ-45 no pide cambios en el dashboard**: se fija con una prueba de
     integración, no con código nuevo.
- Fuera de la API, para avisarte: si el repo del frontend tiene un script de
  contrato contra la API real (spec 018, T11), habría que sumarle este endpoint; no
  lo toco.
