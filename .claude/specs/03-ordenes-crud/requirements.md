# Spec 03 — Órdenes de trabajo: alta, consulta, edición, baja y listado

## Contexto y decisiones

Cuarta spec del backend, módulo `workorders`. Pasa al servidor lo que hoy hacen
`WorkOrdersService` y `work-order.permissions.ts` del frontend sobre JSON
Server (`/work-orders`): el listado con búsqueda, paginación y filtros, el alta,
la edición y la baja, con los permisos por rol y las reglas que hoy solo vigila
el cliente. Tomar, cerrar y liberar una orden es la spec 04. Fuentes: specs 012,
013b y 013d del frontend, sus páginas de listado, alta y edición, el formulario
compartido y `db.json`. Ver `.claude/specs/ROADMAP.md`.

Decisiones tomadas para esta spec:

- **Contrato REST limpio en inglés** (ROADMAP D1): `/work-orders`.
- **Paginación**: parámetros `page` (desde 1, por defecto 1) y `size` (por
  defecto 10, máximo 100); respuesta `{data, page, size, totalItems, totalPages}`.
  Reemplaza a `_page`, `_per_page` y `PaginatedResponse` de JSON Server; el
  frontend lo adapta en su "spec 018". El orden es por `id` ascendente, como hoy.
  **Formato y orden a confirmar al aprobar.**
- Representación de una orden: `id` (string, ROADMAP D2), `title`,
  `description`, `machineRef` (`machineId`, `partId` o `null`, `breadcrumb`,
  `comment`), `type`, `priority`, `status`, `createdAt` (ISO-8601 en UTC),
  `takenBy` (`{id, name, at}` o `null`) y `closingNote` (`{comment, authorId,
  authorName, at}`, ausente si no hay). En esta spec `takenBy` y `closingNote`
  solo se leen y se cargan por seed: los modifica la spec 04.
- **Ids numéricos.** Las órdenes se identifican con un `id` numérico (string en
  la API, `BIGSERIAL` en la base). `db.json` tiene tres órdenes con ids
  alfanuméricos (`jgFCUkYKm4M`, `dW8mYm5vbQs`, `53mjVg8IKEk`, las últimas tres,
  todas `pending`): el seed las renumera `30`, `31` y `32` (REQ-44). **A confirmar
  al aprobar.**
- **Orden de errores** (REQ-46): `401 → 403 → 400 de formato → 404 de la orden de
  la URL → 400 de referencia → 409`, con la validación en `domain` después de
  autorizar, como en las specs 01 y 02. En el alta, el permiso depende del
  `type`: si es válido y no lo puede crear el rol, `403` antes que cualquier `400`.
- **El servidor es la autoridad** (ROADMAP D3): fija el estado inicial
  `pending`, el `createdAt` y el `breadcrumb`. El cliente envía `machineId`,
  `partId` y `comment`, no la ruta.
- **Lo que fija el servidor se ignora si el cliente lo envía** (`id`, `status`,
  `createdAt`, `takenBy`, `closingNote`, `breadcrumb`): un cliente no puede crear
  una orden ya cerrada ni con dueño. **En cambio, lo que el dominio declara
  inmutable se rechaza** si se intenta cambiar (el tipo y la referencia a la
  máquina, requisitos 33 y 34), igual que `legajo` en la spec 01.
- **Una máquina o una parte referenciada por una orden se puede eliminar**: la
  orden conserva su `machineRef` con el `breadcrumb` como historia (013d dejó
  explícito que el snapshot resuelve ese caso y no pide lógica adicional). Por
  eso `work_orders` no tiene clave foránea hacia `machines` ni `parts`.
  **Decisión a confirmar al aprobar**: la alternativa es bloquear esas bajas con
  `409`, que protege la integridad pero deja máquinas y partes imposibles de
  retirar mientras exista una sola orden histórica.
- Límites de texto: `title` de 3 a 150 caracteres y `description` de 10 a 2000,
  ambos sin contar espacios de los bordes; `machineRef.comment` hasta 200. El
  frontend ya exige 3, 10 y 200; **los máximos de título y descripción son
  propuestos** (el frontend no los tiene y la base necesita un tope).
- Permisos (`work-order.permissions.ts`): ver → cualquier rol; crear → team
  leader (`preventivo`, `correctivo`) y producción (`pronto-intervencion`);
  editar → administrador y team leader; eliminar → administrador. La autorización
  vive en `domain`.
- Sin token: `401`; sin permiso: `403`; errores de datos: `400` con el `ApiError`
  de la spec 00.

Restricciones tomadas del frontend, que se mantienen tal cual:

- Tipos: `preventivo`, `correctivo`, `pronto-intervencion`. Prioridades: `low`,
  `medium`, `high`. Estados: `pending`, `in-progress`, `completed`, `cancelled`.
- La máquina es obligatoria para los tres tipos, y se puede elegir solo la
  máquina (sin parte) o una parte de cualquier nivel.
- Al editar solo cambian título, descripción y prioridad.

Alcance descartado explícitamente para esta spec:

- Tomar, cerrar y liberar (spec 04) y cualquier otro cambio de estado.
- Filtrar u ordenar por tipo, máquina o parte (013d lo dejó fuera).
- Fecha estimada de las órdenes preventivas y correctivas: 013b la menciona,
  pero el modelo del frontend no la tiene.
- Especialidad requerida por la orden, adjuntos e historial de cambios.
- Búsqueda por descripción.

## Requisitos

### Listado

### REQ-1: Listado paginado
CUANDO un usuario autenticado solicita `GET /work-orders` sin parámetros
EL SISTEMA DEBERÁ responder `200 OK` con `{data, page, size, totalItems,
totalPages}`, donde `data` contiene la primera página de 10 órdenes ordenadas
por `id` ascendente.

### REQ-2: Página y tamaño
CUANDO se solicita `GET /work-orders?page={p}&size={n}` con valores válidos
EL SISTEMA DEBERÁ devolver en `data` como máximo `n` órdenes, las que
corresponden a la página `p`, con `totalItems` igual a la cantidad de órdenes que
cumplen los filtros y `totalPages` igual a `totalItems / size` redondeado hacia
arriba.

### REQ-3: Parámetros de paginación inválidos
CUANDO `page` es menor que 1, o `size` es menor que 1 o mayor que 100, o alguno
no es un entero
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y el
detalle del parámetro.

### REQ-4: Página fuera de rango
CUANDO se solicita una `page` mayor que `totalPages`
EL SISTEMA DEBERÁ responder `200 OK` con `data` vacío y con `totalItems` y
`totalPages` reales, para que el cliente pueda volver a la última página.

### REQ-5: Sin órdenes
CUANDO no hay ninguna orden que cumpla los criterios
EL SISTEMA DEBERÁ responder `200 OK` con `data` vacío, `totalItems` en `0` y
`totalPages` en `0`.

### REQ-6: Búsqueda por título
CUANDO se solicita `GET /work-orders?title={texto}`
EL SISTEMA DEBERÁ devolver solo las órdenes cuyo título contiene ese texto, sin
distinguir mayúsculas de minúsculas y sin contar los espacios de los bordes del
texto buscado.

### REQ-7: Caracteres especiales en la búsqueda
CUANDO el texto de `title` contiene `%` o `_`
EL SISTEMA DEBERÁ tratarlos como caracteres literales y no como comodines.

### REQ-8: Búsqueda vacía
CUANDO `title` llega vacío o compuesto solo de espacios
EL SISTEMA DEBERÁ ignorar ese criterio y no filtrar por título.

### REQ-9: Filtro por estado
CUANDO se solicita `GET /work-orders?status={estado}` con uno de los cuatro
estados válidos
EL SISTEMA DEBERÁ devolver solo las órdenes con ese estado, y `totalItems` y
`totalPages` deben reflejar solo esas órdenes.

### REQ-10: Filtro por prioridad
CUANDO se solicita `GET /work-orders?priority={prioridad}` con una de las tres
prioridades válidas
EL SISTEMA DEBERÁ devolver solo las órdenes con esa prioridad, y `totalItems` y
`totalPages` deben reflejar solo esas órdenes.

### REQ-11: Filtros combinados
CUANDO se combinan `title`, `status` y `priority` en la misma solicitud
EL SISTEMA DEBERÁ devolver solo las órdenes que cumplen los tres criterios a la
vez.

### REQ-12: Valor de filtro inválido
CUANDO `status` o `priority` tiene un valor fuera de los válidos
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y el
detalle del parámetro, en vez de devolver una lista vacía.

### Consulta

### REQ-13: Consulta de una orden
CUANDO un usuario autenticado solicita `GET /work-orders/{id}` con el id de una
orden existente
EL SISTEMA DEBERÁ responder `200 OK` con la orden completa, incluidos `machineRef`,
`takenBy` y `closingNote` cuando existan.

### REQ-14: Consulta de una orden inexistente
CUANDO se solicita `GET /work-orders/{id}` con un id que no existe o que no es
numérico
EL SISTEMA DEBERÁ responder `404 Not Found` con el `ApiError` de la spec 00.

### Alta

### REQ-15: Alta de una orden
CUANDO un usuario autorizado envía `POST /work-orders` con `title`,
`description`, `type`, `priority` y `machineRef` válidos
EL SISTEMA DEBERÁ crear la orden y responder `201 Created` con la orden creada,
con un `id` generado por el servidor, `status` `pending`, `createdAt` con la
hora del servidor, `takenBy` en `null` y sin `closingNote`.

### REQ-16: Tipos que puede crear cada rol
CUANDO un `team-leader-mantenimiento` crea una orden `preventivo` o `correctivo`,
o un `personal-produccion` crea una orden `pronto-intervencion`
EL SISTEMA DEBERÁ aceptarla.

### REQ-17: Alta sin permiso
CUANDO un `administrador` o un `tecnico` intenta crear una orden de cualquier
tipo, o un `team-leader-mantenimiento` crea una `pronto-intervencion`, o un
`personal-produccion` crea una `preventivo` o `correctivo`
EL SISTEMA DEBERÁ responder `403 Forbidden` y no crear la orden.

### REQ-18: Título y descripción
CUANDO se crea o se edita una orden con `title` de menos de 3 o más de 150
caracteres, o `description` de menos de 10 o más de 2000, contando sin los
espacios de los bordes, o cualquiera de los dos ausente
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y el
detalle del campo.

### REQ-19: Tipo y prioridad válidos
CUANDO se crea una orden con `type` ausente o fuera de los tres tipos válidos, o
se crea o se edita con `priority` ausente o fuera de las tres prioridades válidas
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y el
detalle del campo.

### REQ-20: Texto recortado
CUANDO se crea o se edita una orden con espacios al principio o al final de
`title` o `description`
EL SISTEMA DEBERÁ guardarlos sin esos espacios.

### REQ-21: La máquina es obligatoria
CUANDO se crea una orden, de cualquiera de los tres tipos, sin `machineRef` o sin
`machineRef.machineId`
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y no
crear la orden.

### REQ-22: Máquina inexistente
CUANDO se crea una orden con un `machineId` que no corresponde a ninguna máquina
(incluido un valor vacío o que no es un id numérico)
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `MACHINE_NOT_FOUND` y no
crear la orden.

### REQ-23: Orden sobre la máquina completa
CUANDO se crea una orden con `partId` nulo o ausente
EL SISTEMA DEBERÁ guardar `partId` en `null` y un `breadcrumb` igual al nombre de
la máquina.

### REQ-24: Orden sobre una parte
CUANDO se crea una orden con un `partId` que corresponde a una parte de esa
máquina, en cualquier nivel del árbol
EL SISTEMA DEBERÁ armar el `breadcrumb` con el nombre de la máquina y los nombres
de todos los ancestros de la parte hasta la parte misma, en ese orden y
separados por ` > `.

### REQ-25: Parte inexistente
CUANDO se crea una orden con un `partId` que no corresponde a ninguna parte
(incluido un valor vacío o que no es un id numérico)
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `PART_NOT_FOUND` y no
crear la orden.

### REQ-26: Parte de otra máquina
CUANDO se crea una orden con un `partId` que corresponde a una parte de una
máquina distinta de `machineId`
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `PART_OTHER_MACHINE` y
no crear la orden.

### REQ-27: El comentario de falla va separado
CUANDO se crea una orden con `machineRef.comment`
EL SISTEMA DEBERÁ guardarlo recortado y como campo propio, sin incluirlo en el
`breadcrumb`; y CUANDO el comentario está ausente, EL SISTEMA DEBERÁ guardarlo
como cadena vacía.

### REQ-28: Comentario demasiado largo
CUANDO se crea una orden con un `machineRef.comment` de más de 200 caracteres
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y no
crear la orden.

### REQ-29: El breadcrumb es una foto
CUANDO, después de crear una orden, se renombra su máquina o alguna de las partes
de su cadena
EL SISTEMA DEBERÁ seguir devolviendo en esa orden el `breadcrumb` con los nombres
que tenían al crearla.

### REQ-30: Máquina o parte eliminada después de usarla
CUANDO se elimina una máquina o una parte que una orden referencia
EL SISTEMA DEBERÁ permitir la baja y conservar en esa orden su `machineRef`
completo, con el `breadcrumb` original.

### REQ-31: El cliente no fija lo que decide el servidor
CUANDO el cuerpo de `POST /work-orders` incluye `id`, `status`, `createdAt`,
`takenBy`, `closingNote` o `machineRef.breadcrumb`
EL SISTEMA DEBERÁ ignorarlos y crear la orden con `status` `pending`, `takenBy`
en `null`, sin `closingNote`, el `createdAt` del servidor y el `breadcrumb`
calculado.

### Edición

### REQ-32: Edición de una orden
CUANDO un `administrador` o un `team-leader-mantenimiento` envía
`PUT /work-orders/{id}` con `title`, `description` y `priority` válidos para una
orden existente
EL SISTEMA DEBERÁ actualizar esos tres campos y responder `200 OK` con la orden
completa actualizada.

### REQ-33: El tipo no se edita
CUANDO el cuerpo de `PUT /work-orders/{id}` incluye un `type` distinto del de la
orden
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y no
modificar la orden.

### REQ-34: La máquina y la parte no se editan
CUANDO el cuerpo de `PUT /work-orders/{id}` incluye un `machineRef` con un
`machineId`, un `partId` o un `comment` distinto del de la orden
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y no
modificar la orden.

### REQ-35: La edición no cambia el estado
CUANDO el cuerpo de `PUT /work-orders/{id}` incluye `status`, `takenBy`,
`closingNote`, `createdAt`, `id` o `machineRef.breadcrumb` distintos de los
actuales
EL SISTEMA DEBERÁ ignorarlos y conservar los de la orden.

### REQ-36: Edición en cualquier estado
CUANDO se edita una orden que está `in-progress`, `completed` o `cancelled`
EL SISTEMA DEBERÁ aceptar la edición y conservar su estado, su dueño y su nota
de cierre.

### REQ-37: Edición de una orden inexistente
CUANDO se envía `PUT /work-orders/{id}` con un id que no existe o que no es
numérico
EL SISTEMA DEBERÁ responder `404 Not Found` y no crear ninguna orden.

### REQ-38: Permiso de edición
CUANDO un usuario autenticado con rol `personal-produccion` o `tecnico` envía
`PUT /work-orders/{id}`
EL SISTEMA DEBERÁ responder `403 Forbidden` y no modificar la orden.

### Baja

### REQ-39: Baja de una orden
CUANDO un `administrador` envía `DELETE /work-orders/{id}` para una orden
existente, en cualquier estado
EL SISTEMA DEBERÁ eliminarla y responder `204 No Content`.

### REQ-40: Baja de una orden inexistente
CUANDO se envía `DELETE /work-orders/{id}` con un id que no existe o que no es
numérico
EL SISTEMA DEBERÁ responder `404 Not Found`.

### REQ-41: Permiso de baja
CUANDO un usuario autenticado con un rol distinto de `administrador` (incluido
`team-leader-mantenimiento`) envía `DELETE /work-orders/{id}`
EL SISTEMA DEBERÁ responder `403 Forbidden` y no eliminar la orden.

### Permisos de lectura

### REQ-42: Lectura para todos los roles
CUANDO un usuario autenticado, con cualquiera de los cuatro roles, solicita
`GET /work-orders` o `GET /work-orders/{id}`
EL SISTEMA DEBERÁ responder con los datos y no con `403`.

### REQ-46: Orden de los errores
CUANDO una petición a `/work-orders` reúne más de una causa de error
EL SISTEMA DEBERÁ responder el primero de este orden: `401` (sin token); `403`
(el rol no puede hacer la operación; en el alta, también cuando el `type` enviado
es válido pero no es uno de los que su rol puede crear); `400` de formato (todos
los errores de los campos juntos, incluido un `type` ausente o inválido y los
parámetros de paginación y filtros); `404` de la orden de la URL; `400` de
referencia (`MACHINE_NOT_FOUND`, `PART_NOT_FOUND`, `PART_OTHER_MACHINE`, y el
intento de cambiar el tipo o la máquina en la edición, REQ-33 y REQ-34).

### Esquema, datos y documentación

### REQ-43: Esquema de órdenes
CUANDO la aplicación arranca contra una base que ya tiene las migraciones hasta
la spec 02 (`V6`)
EL SISTEMA DEBERÁ aplicar una migración de Flyway `V7` (el seed de dev de REQ-44,
`V7_1`) que cree `work_orders` con las
columnas de la orden, restricciones que limiten `type`, `priority` y `status` a
sus valores válidos, y sin clave foránea hacia `machines` ni `parts`.

### REQ-44: Datos de prueba de dev
CUANDO la aplicación arranca con el perfil `dev`
EL SISTEMA DEBERÁ dejar cargadas las 32 órdenes de `db.json` (12 `pending`,
9 `in-progress`, 9 `completed` y 2 `cancelled`) con sus ids `1` a `29`, y las
tres de id alfanumérico renumeradas `30`, `31` y `32` en el orden de `db.json`,
más `machineRef`, `takenBy` y `closingNote`; los `createdAt` que `db.json` guarda
sin zona horaria se interpretan como UTC; los dueños y autores de cierre que
`db.json` identifica con el id de usuario `2` y `5` del frontend quedan a nombre
de los usuarios `tecnico` y `electricista` respectivamente (`takenBy.id` y
`closingNote.authorId` son los ids de esos usuarios en el backend, que no
coinciden con los del frontend, y los nombres se conservan); los ids de las
órdenes nuevas continúan después del mayor (`33`); y no se cargan en ningún otro
perfil.

### REQ-45: Documentación OpenAPI
CUANDO se accede a la documentación de springdoc
EL SISTEMA DEBERÁ mostrar todos los endpoints de `/work-orders` generados desde
las anotaciones del código, con el esquema de seguridad `bearerAuth`.

## Auto-revisión

- Un comportamiento por requisito: REQ-18, REQ-19 y REQ-20 agrupan campos que
  comparten el mismo resultado (`400` con `VALIDATION_ERROR`), como en las specs
  01 y 02; REQ-17 junta las cuatro combinaciones sin permiso porque es una sola
  regla de la matriz rol-tipo.
- Caminos no felices cubiertos: paginación inválida (REQ-3), página fuera de
  rango y estado vacío (REQ-4, REQ-5), filtros inválidos (REQ-12), inexistentes
  (REQ-14, REQ-37, REQ-40), datos inválidos (REQ-18, REQ-19, REQ-21, REQ-28),
  referencias inválidas (REQ-22, REQ-25, REQ-26), campos inmutables (REQ-33,
  REQ-34) y permisos (REQ-17, REQ-38, REQ-41).
- Sin adjetivos no verificables: cada criterio fija un código HTTP, un `code` de
  error o un estado concreto de los datos.
- Puntos que decidí yo y conviene que confirmes:
  1. Formato de paginación, tamaño por defecto y máximo, y orden ascendente por
     `id` (contexto).
  2. **Máquinas y partes referenciadas se pueden eliminar** (REQ-30, contexto).
  3. Los máximos de 150 y 2000 caracteres (REQ-18) son propuestos.
  4. **Lo que fija el servidor se ignora** (REQ-31, REQ-35) pero **el tipo y la
     máquina se rechazan** (REQ-33, REQ-34): es la misma regla que `legajo` en la
     spec 01, y evita que un `PUT` con el objeto completo (como manda hoy el
     frontend) falle por campos de flujo que no cambió.
  5. **Editar órdenes en cualquier estado** (REQ-36): es lo que el frontend
     permite hoy, aunque quizás convenga bloquear la edición de las cerradas.
  6. Los códigos nuevos (`MACHINE_NOT_FOUND`, `PART_NOT_FOUND`,
     `PART_OTHER_MACHINE`) son propuestos.
  7. `totalPages` en `0` para una lista vacía (REQ-5): el frontend hace
     `Math.max(1, pages)`, así que lo tolera.
- Correcciones aplicadas tras la primera lectura: ids alfanuméricos de `db.json`
  renumerados (contexto y REQ-44; se verificó que `machineRef`, `breadcrumb`,
  dueños y notas de cierre del resto de `db.json` son coherentes con el seed de la
  spec 02); migraciones desde `V7` (REQ-43); orden de errores con el permiso
  dependiente del `type` (REQ-46); `machineId` o `partId` vacío o no numérico como
  referencia inexistente (REQ-22, REQ-25); `machineRef.breadcrumb` ignorado en la
  edición (REQ-35); id no numérico como inexistente en `PUT` y `DELETE`
  (REQ-37, REQ-40).
- Dependencia con la spec 02: REQ-44 exige que las máquinas y partes de prueba
  conserven los ids de `db.json` (`machineId` y `partId` de las órdenes apuntan a
  ellos); se ajustó REQ-34 de la spec 02 en consecuencia.
