# Roadmap post-auditoría — angular-enterprise-lab (frontend + API)

Fecha: 2026-10-22. Base: auditoría del backend (rama `feat/cancelar-orden`, 1123 tests en verde) y auditoría del frontend (master, specs 00 a 21).

Repos:

- Frontend: `C:\Programs\CMMS\angular-enterprise-lab` (Angular 22, specs hasta la 21, la siguiente libre es la 22).
- API: `angular-enterprise-lab-api` (Java 21, Spring Boot 3.5, PostgreSQL, Flyway, specs 00 a 05).

## 0. Cómo usar este documento (instrucciones para Claude Code)

- Este archivo es el plan general. Cada spec se ejecuta por separado con `sdd-run`, en una sesión nueva (`/clear` entre specs), siguiendo el flujo completo del `CLAUDE.md` de cada repo: `requirements.md` → `design.md` → `tasks.md`, con aprobación mía entre fases.
- Cada repo se trabaja desde su propia carpeta y no se toca el otro. Los cambios de contrato se acuerdan por spec en ambos repos y se ejecutan en el orden de la sección 4.
- Numeración: los números de spec de este documento son propuestos. Antes de crear una carpeta, leé el `ROADMAP.md` del repo y usá el siguiente número libre. Si ya existe una spec para lo que se pide, es una enmienda (NN-A, NN-B): se edita esa carpeta y no se crea una segunda.
- Verificá antes de asumir. La auditoría se hizo leyendo el código, no ejecutando la app. Si algo de este documento no coincide con el código, vale el código y se me avisa.
- Migraciones de Flyway: nunca se edita una migración ya aplicada (checksum). Todo cambio de esquema va en una migración nueva.
- Contrato de errores (se mantiene): `{code, message, timestamp, path, details?}`. Estado `cancelled` (doble l). Los ids de órdenes son strings numéricos.
- Cada requisito lleva su estado de fallo explícito: qué comportamiento haría fallar el test, no solo el caso de éxito.

Regla de despliegue entre repos:

- Cuando una spec restringe (quita un endpoint, endurece una validación), se despliega primero el frontend y después la API.
- Cuando agrega (campo nuevo, endpoint nuevo), se despliega primero la API y después el frontend.
- Si una spec exige un despliegue conjunto, lo dice.

Al cerrar cada spec: actualizar `ROADMAP.md`, `README.md` y `CLAUDE.md` del repo, y correr lint, tests y build (frontend) o `./gradlew build` (API).

## 1. Decisiones vigentes

| # | Tema | Decisión | Origen |
|---|---|---|---|
| D1 | Cancelación | La cancela únicamente el dueño de la orden (el técnico que la tomó), con motivo obligatorio. Administrador y team leader no cancelan; para sacarle una orden a un técnico usan `release`. | Cristian, 2026-10-22 |
| D2 | Órdenes cerradas | `completed` y `cancelled` son de solo lectura para siempre: no se editan ni se borran, ni siquiera el administrador. | Acordado antes, confirmado |
| D3 | Comentario de cierre | Al completar: mínimo 200 y máximo 500 caracteres. Al cancelar (motivo): mínimo 50 y máximo 500. Sin contar los espacios de los bordes. | Criterio aprobado |
| D4 | Fechas | La API expone `startDate` (alias de `taken_at`) y `endDate` (alias de `closed_at`), sin columnas nuevas. El tiempo promedio se calcula desde la toma. `release` queda registrado en una tabla de eventos. | Criterio aprobado |
| D5 | Especialidad | La orden lleva `requiredSpecialty` (`mecanico` o `electricista`), elegida por quien la crea. Al tomar se aplica `canTechnicianHandle` (`general` cubre ambas) además del tipo de equipo. | Criterio aprobado |
| D6 | Equipos | Un técnico pertenece a un solo equipo, y el tipo del equipo tiene que coincidir con el `teamType` del técnico. | Criterio aprobado |
| D7 | Usuarios | Alta de usuarios por API (administrador), con validación de legajo para el rol técnico. El primer administrador se crea por variables de entorno (bootstrap). | Criterio aprobado |
| D8 | Demo pública | Rol invitado de solo lectura, con acceso desde un botón "Entrar como invitado" en la portada. | Pedido de Cristian (portfolio) |

## 2. Mapa de cambios

| ID | Cambio | Prioridad | API | Frontend | Depende de |
|---|---|---|---|---|---|
| F0 | Verificaciones previas | Alta | — | — | — |
| C1 | Cancelación solo por el dueño (D1) | Alta | enmienda 04-A | enmienda 20-A | F0 |
| C2 | Reglas de cierre: solo lectura y comentarios (D2, D3) | Alta | enmienda 04-B | enmienda 17-A | C1 |
| C3 | Hardening de seguridad | Alta | spec 06 | enmienda 19-A | F0 |
| C4 | Gestión de usuarios y bootstrap (D7) | Alta | spec 07 | spec 22 | C3 |
| C5 | Invitado / demo pública (D8) | Media | spec 08 | spec 23 | C4 |
| C6 | Trazabilidad: fechas y eventos (D4) | Media | spec 09 | spec 24 | C1, C2 |
| C7 | Especialidad requerida y coherencia de equipos (D5, D6) | Media | spec 10 | spec 25 | C2 |
| C8 | Pruebas end-to-end | Media | — | spec 26 | C1 a C7 |
| C9 | Calidad operativa | Baja | spec 11 | spec 27 | — |

Si el `ROADMAP.md` de la API ya reserva la 06 para "operación y producción", absorber ahí el alcance de C9 y renumerar el resto.

## 3. Detalle de cada cambio

### F0 — Verificaciones previas (sin spec)

Antes de empezar, confirmar y dejar por escrito en `notes.md`:

1. Estado de la rama `feat/cancelar-orden` de la API: ¿está mergeada a master? Los commits recientes del frontend ya incluyen la spec 20 mergeada.
2. Cómo llega `/api` al backend en producción. El frontend llama a `/api` (relativa). En desarrollo la reescribe `proxy.conf.json`, pero `nginx.conf` no tiene `location /api`. Hay que confirmar si el proxy del VPS (carpeta `/opt/web-stack`, fuera de ambos repos) enruta `/api` por path hacia el contenedor de la API, con o sin StripPrefix.
3. Si `vars.DEPLOY_ENABLED` está en `true` en cada repo.
4. Variables de entorno reales en producción: `JWT_SECRET`, `DB_PASSWORD`, `app.cors.allowed-origins`.
5. Que `./gradlew test` y `pnpm exec ng test --no-watch` pasan en limpio en la máquina local.

Si el punto 2 no está resuelto, se resuelve como tarea de infraestructura antes de cualquier despliegue (ver sección 6).

### C1 — Cancelación solo por el dueño (Alta)

Objetivo: que la única vía de cancelación sea el cierre del técnico dueño con motivo (D1).

**Frontend — enmienda 20-A**

- Eliminar `CANCEL_ROLES` y `canCancelWorkOrder` de `work-order.permissions.ts`.
- Eliminar el botón "Cancelar orden", el modal y su lógica en `work-orders-list` y en `work-order-detail`.
- Eliminar `WorkOrdersService.cancel()` y sus tests.
- Marcar la spec 20 como reemplazada por esta enmienda en el `ROADMAP.md`.
- En `work-order-resolve`, cuando el resultado elegido es `cancelled`, el campo se rotula "Motivo de la cancelación" y el texto de ayuda lo explica. La validación de longitud por resultado se hace en C2.

**API — enmienda 04-A**

- Eliminar `POST /work-orders/{id}/cancel`, su caso de uso y la regla en `WorkOrderPermissions`.
- Nueva migración que restituya las invariantes (una orden `cancelled` siempre tiene dueño y nota de cierre). Revisar V8 y V9: `V9__work_orders_cancel_pending.sql` relajó los CHECK para permitir cancelar una orden `pending` sin dueño. Antes de endurecer, verificar si existen órdenes `cancelled` sin dueño (en la base de producción y en el seed) y definir en el diseño cómo tratarlas.
- `POST /close` con `outcome: cancelled` se mantiene: dueño de la orden en `in-progress`, equipo habilitado, comentario obligatorio.
- Actualizar README (tabla de endpoints) y OpenAPI.

**Estado de fallo / criterios**

- `POST /work-orders/{id}/cancel` responde un error (no 200) para cualquier rol.
- Administrador y team leader no pueden cancelar una orden `pending` ni `in-progress`.
- Un técnico que no es dueño no puede cerrar la orden como `cancelled` (409 `WORK_ORDER_TAKEN_BY_OTHER`).
- Cancelar con motivo vacío o más corto que el mínimo responde 400.
- El frontend no muestra ningún botón de cancelar a administrador ni a team leader.

Despliegue: frontend primero, API después.

### C2 — Reglas de cierre: solo lectura y comentarios (Alta)

Objetivo: D2 y D3.

**API — enmienda 04-B** (alcanza también a la spec 03: PUT y DELETE de órdenes)

- `PUT /work-orders/{id}` y `DELETE /work-orders/{id}` sobre una orden `completed` o `cancelled` responden 409 `WORK_ORDER_CLOSED` con `details.status`. Respaldarlo en la sentencia SQL (`UPDATE … WHERE status IN ('pending','in-progress')`) para que no dependa solo de una lectura previa.
- Comentario de cierre: 200 a 500 caracteres si `outcome` es `completed`; 50 a 500 si es `cancelled`. Hoy la validación vive en `WorkOrderFlowService` (50 a 500). Agregar `@Size` en el DTO solo si no duplica la regla de dominio.
- Verificar que no haya un CHECK de longitud mínima en la base que impida leer o conservar notas antiguas. La regla nueva aplica a cierres nuevos: las órdenes cerradas existentes con notas más cortas no se tocan.
- Actualizar README, OpenAPI (nuevo código `WORK_ORDER_CLOSED`) y las specs 03 y 04.

**Frontend — enmienda 17-A**

- Ocultar "Editar" y "Eliminar" en el listado y en el detalle cuando la orden está cerrada (`canEditWorkOrder` y `canDeleteWorkOrder` pasan a recibir la orden).
- La ruta `/work-orders/:id/edit`: si la orden cargada está cerrada, mostrar el aviso "Orden cerrada" en lugar del formulario.
- Traducir `WORK_ORDER_CLOSED` a un error tipado, mostrar el aviso y recargar la orden.
- Separar las constantes: `COMPLETION_NOTE_MIN_LENGTH = 200`, `CANCELLATION_NOTE_MIN_LENGTH = 50`, máximo 500 en ambos. `isValidClosingComment(value, outcome)` recibe el resultado. En `work-order-resolve`, el validador, el contador ("n / mínimo") y el mensaje de error dependen del resultado elegido y se revalidan al cambiarlo.
- Cuidado con los datos viejos: `isWorkOrderClosingNote` usa hoy `isValidClosingComment` para validar las órdenes que llegan de la API. Si se endurece sin separar, las órdenes cerradas con notas de 50 a 199 caracteres dejarían de cargar. La guarda de lectura solo debe exigir texto no vacío y el máximo; el mínimo aplica únicamente al enviar el formulario.

**Estado de fallo / criterios**

- PUT y DELETE sobre una orden `completed` y sobre una `cancelled` responden 409 `WORK_ORDER_CLOSED`, incluso con rol administrador.
- Cerrar como `completed` con 199 caracteres responde 400; con 200 pasa.
- Cancelar con 49 caracteres responde 400; con 50 pasa.
- Una orden cerrada con una nota de 60 caracteres (dato antiguo) sigue cargando en el detalle.
- Ninguna acción de escritura aparece sobre una orden cerrada, para ningún rol.

Despliegue: frontend primero, API después.

### C3 — Hardening de seguridad (Alta)

> **Estado 2026-10-22: lado API hecho (spec 06, `.claude/specs/06-hardening-seguridad/`).** Pendiente: enmienda 19-A del frontend y, antes de desplegar la API, confirmar en F0 que producción define `JWT_SECRET` y `DB_PASSWORD`.

**API — spec 06**

- Rate limiting en `POST /auth/login` por IP y por usuario. Respuesta 429 con `ApiError` (`code: TOO_MANY_ATTEMPTS`) y cabecera `Retry-After`. Implementación en memoria (hay una sola instancia); dejar documentada la limitación.
- Swagger y `/v3/api-docs` fuera de producción: `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.enabled=false` en la configuración base, activados solo en `dev`. El `permitAll` correspondiente queda condicionado al perfil.
- `DB_PASSWORD` sin valor por defecto fuera de `dev`.
- Validar `JWT_SECRET` al arrancar: mínimo 32 bytes, falla con mensaje claro.
- Excepciones estándar de Spring MVC con `ApiError`: 405 (método no soportado), 415 (tipo de contenido), 400 (parámetro faltante o mal tipado) y 404 (recurso inexistente). Hoy caen en `@ExceptionHandler(Exception.class)` y responden 500.
- Reintentos agotados en `WorkOrderFlowService.attempt()`: 409 controlado en lugar de `IllegalStateException` → 500.
- Documentar `app.cors.allowed-origins` como variable de producción.

**Frontend — enmienda 19-A:** mostrar el mensaje de 429 en el login (con el tiempo de espera si viene en `Retry-After`).

**Estado de fallo / criterios**

- N intentos fallidos seguidos desde la misma IP o contra el mismo usuario hacen que el siguiente responda 429, aunque la contraseña sea correcta.
- Con el perfil de producción, `GET /swagger-ui.html` y `GET /v3/api-docs` no responden 200.
- Sin `DB_PASSWORD` fuera de `dev`, la aplicación no arranca.
- `JWT_SECRET` de 16 bytes: la aplicación no arranca.
- `DELETE /work-orders` (sin id), un POST con `Content-Type: text/plain` y `GET /work-orders?page=abc` responden 405, 415 y 400, nunca 500.

Despliegue: API primero.

### C4 — Gestión de usuarios y bootstrap (Alta)

**API — spec 07**

- `POST /users` (administrador): `username`, `password`, `displayName`, `email`, `role`, `legajo` (obligatorio si el rol es `tecnico` y prohibido en los demás). El legajo debe existir en el maestro de técnicos (400 `UNKNOWN_TECHNICIAN`) y no estar vinculado a otro usuario (409 `TECHNICIAN_ALREADY_HAS_USER`). Política de contraseña mínima definida en el diseño. Hash BCrypt.
- `GET /users` (administrador), activar y desactivar usuario (`enabled`), restablecer contraseña. Un usuario desactivado no puede iniciar sesión (misma respuesta que credenciales inválidas) y su token deja de servir en `/auth/me`.
- Bootstrap del primer administrador: variables `BOOTSTRAP_ADMIN_USERNAME` y `BOOTSTRAP_ADMIN_PASSWORD`. Al arrancar, si la tabla de usuarios está vacía y las variables existen, se crea el administrador. Si hay usuarios, no hace nada. Nunca se registra la contraseña en los logs ni se sobrescribe un usuario existente. Sin variables y con tabla vacía, arranca y deja una advertencia.
- Documentar en README cómo obtener el primer usuario en producción.

**Frontend — spec 22:** feature `users` solo para administrador: listado, alta (con selector de legajo cuando el rol es técnico), activar y desactivar, restablecer contraseña. Entrada en la navegación visible solo para administrador.

**Estado de fallo / criterios**

- Un rol distinto de administrador recibe 403 en todos los endpoints de `/users`.
- Crear un usuario técnico con un legajo inexistente responde 400; con un legajo ya vinculado, 409.
- Con la base vacía y las variables definidas, el administrador puede iniciar sesión; con la base ya poblada, el bootstrap no cambia nada.
- Un usuario desactivado no puede iniciar sesión.

Despliegue: API primero.

### C5 — Invitado / demo pública (Media)

**API — spec 08**

- Rol `invitado` (migración que amplía el CHECK de roles). Solo lectura en todos los módulos que el frontend muestra; ninguna escritura; sin acceso a `/users`.
- Acceso sin contraseña: `POST /auth/guest`, habilitado solo si `app.guest.enabled=true`, que emite un JWT del usuario invitado con vigencia corta (por ejemplo 30 minutos). Con rate limiting de C3. Alternativa a evaluar en el diseño: un usuario invitado con credenciales públicas; si se elige, justificarlo.
- El usuario invitado se crea por el mismo mecanismo de bootstrap de C4.

**Frontend — spec 23**

- Rol invitado en la unión discriminada de `AuthUser` (como los roles de personal) con `isAuthUser` y `toAuthUser` actualizados.
- Todas las funciones `can*` devuelven false para el invitado. Ninguna acción de escritura se muestra.
- Botón "Entrar como invitado" en la portada y en el login. Aviso permanente "Modo demo: solo lectura" en el header.

**Estado de fallo / criterios**

- Con `app.guest.enabled=false`, `POST /auth/guest` no emite token.
- El invitado recibe 403 en cualquier POST, PUT, PATCH y DELETE.
- Un token de invitado vencido produce el flujo normal de sesión expirada.

Despliegue: API primero.

### C6 — Trazabilidad: fechas y eventos (Media)

**API — spec 09**

- La respuesta de una orden incluye `startDate` (`taken_at`, null mientras esté pendiente) y `endDate` (`closed_at`, null mientras no esté cerrada). Se mantienen `takenBy.at` y `closingNote.at` por compatibilidad.
- Tabla `work_order_events` (id, orden, tipo `taken|released|closed`, usuario que actuó, nombre como snapshot, fecha, resultado si es cierre). Se registra en `take`, `release` y `close`. `GET /work-orders/{id}/events` para cualquier usuario autenticado.
- Definir en el diseño si se hace backfill de eventos para las órdenes existentes a partir de `taken_at` y `closed_at`.
- Dashboard: `averageResolutionMinutes` pasa a medirse como `closed_at - taken_at`, solo sobre órdenes `completed` con `taken_at` no nulo. Opcional: `averageWaitMinutes` (`taken_at - created_at`).

**Frontend — spec 24**

- Mostrar `startDate` y `endDate` en el detalle.
- Línea de tiempo de eventos en el detalle (quién tomó, quién liberó, quién cerró y cuándo).
- Rotular el indicador del dashboard como "Tiempo promedio de intervención".

**Estado de fallo / criterios**

- Tomar una orden completa `startDate`; cerrarla o cancelarla completa `endDate`.
- `release` deja un evento con quién y cuándo, y la orden vuelve a `pending` con `startDate` nulo.
- El promedio de resolución no cambia si se modifica `createdAt` (ya no depende de él).

Despliegue: API primero.

### C7 — Especialidad requerida y coherencia de equipos (Media)

**API — spec 10**

- Columna `required_specialty` (`mecanico`, `electricista`) en `work_orders`. Obligatoria en el alta de órdenes nuevas; inmutable como el tipo. Las órdenes existentes quedan con NULL, que significa "cualquier especialidad" (decidir en el diseño y documentarlo).
- `POST /work-orders/{id}/take`: además del tipo de equipo, se aplica `canTechnicianHandle`: la especialidad del técnico (`general` cubre ambas) debe coincidir con `requiredSpecialty`. Si no, 403 con código propio (`TECHNICIAN_SPECIALTY_MISMATCH`).
- Equipos: `UNIQUE (technician_id)` en `team_members`; al crear o editar un equipo, el tipo del equipo debe coincidir con el `teamType` de cada miembro (400 `TEAM_TYPE_MISMATCH`) y un técnico no puede estar en más de un equipo (409 `TECHNICIAN_ALREADY_IN_TEAM`). La migración debe detectar y resolver duplicados existentes antes de crear la restricción.
- Documentar que la autorización sigue leyendo `technicians.team_type` y que la validación de membresía garantiza que ambas fuentes coinciden.

**Frontend — spec 25**

- Selector de especialidad requerida en el formulario de alta (obligatorio); se muestra en el listado y en el detalle.
- `canTakeWorkOrder` usa `canTechnicianHandle` (hoy la función existe pero no se usa en el flujo de tomar).
- Formularios de equipos: mostrar `TEAM_TYPE_MISMATCH` y `TECHNICIAN_ALREADY_IN_TEAM` junto al campo.

**Estado de fallo / criterios**

- Un técnico electricista no puede tomar una orden `mecanico` aunque su equipo atienda el tipo (403).
- Un técnico `general` puede tomar ambas.
- No se puede agregar a un equipo un técnico de otro tipo ni uno que ya pertenece a otro equipo.
- Crear una orden sin `requiredSpecialty` responde 400.

Despliegue: conjunto (el alta de órdenes cambia de contrato). Se acepta una ventana corta de inconsistencia entre ambos despliegues.

### C8 — Pruebas end-to-end (Media)

**Frontend — spec 26:** Playwright contra el stack real (PostgreSQL + API con perfil `dev` + build del frontend), levantado con Docker Compose en un job de CI.

Escenarios mínimos:

1. Login con cada rol y redirección correcta.
2. Team leader crea una orden preventiva con máquina, parte y especialidad.
3. Técnico habilitado toma la orden y la completa con un comentario de 200 caracteres.
4. Técnico dueño cancela otra orden con motivo; el administrador no ve ninguna acción de cancelar.
5. Una orden cerrada no muestra Editar ni Eliminar para ningún rol.
6. Dos técnicos intentan tomar la misma orden: uno gana y el otro ve el aviso.
7. Entrar como invitado: navega y no ve ninguna acción de escritura.
8. Login con rate limiting: tras los intentos configurados, el mensaje de espera.

### C9 — Calidad operativa (Baja)

**API — spec 11**

- Dependabot (Gradle, GitHub Actions, Docker) y escaneo de vulnerabilidades en CI. Revisar `nimbus-jose-jwt` 9.37.4 y `springdoc` 2.9.1.
- `HEALTHCHECK` y `-XX:+ExitOnOutOfMemoryError` en el Dockerfile; no publicar el puerto 5432 en `docker-compose.yaml`.
- Fijar las acciones de GitHub por SHA; revisar el uso de `root@` en el deploy.
- Anotar 401 y 403 de forma global en OpenAPI; versión de la API tomada del build.
- Versionar los subagentes del flujo SDD en `.claude/agents/` y borrar `spec-verifier.md.bak`.
- Evaluar `outOfOrder` o separar la numeración del seed en Flyway.

**Frontend — spec 27**

- Dependabot y acciones fijadas por SHA.
- Cabeceras de seguridad en `nginx.conf` (Content-Security-Policy, X-Content-Type-Options, Referrer-Policy, X-Frame-Options), `HEALTHCHECK` en el Dockerfile.
- Presupuestos de bundle y auditoría de accesibilidad automatizada en CI.
- Spec 18·O2 (skeleton de carga) y 19·T12 (filtros `closedFrom`/`closedTo`) si siguen siendo necesarios.

## 4. Orden de ejecución

| Paso | Repo | Cambio | Despliegue |
|---|---|---|---|
| 0 | ambos | F0 verificaciones | — |
| 1 | Frontend | C1 · enmienda 20-A | primero |
| 2 | API | C1 · enmienda 04-A | segundo |
| 3 | Frontend | C2 · enmienda 17-A | primero |
| 4 | API | C2 · enmienda 04-B | segundo |
| 5 | API | C3 · spec 06 | primero |
| 6 | Frontend | C3 · enmienda 19-A | segundo |
| 7 | API | C4 · spec 07 | primero |
| 8 | Frontend | C4 · spec 22 | segundo |
| 9 | API | C5 · spec 08 | primero |
| 10 | Frontend | C5 · spec 23 | segundo |
| 11 | API | C6 · spec 09 | primero |
| 12 | Frontend | C6 · spec 24 | segundo |
| 13 | API y Frontend | C7 · specs 10 y 25 | conjunto |
| 14 | Frontend | C8 · spec 26 | — |
| 15 | API y Frontend | C9 · specs 11 y 27 | — |

Los pasos 5 y 6 (hardening) no dependen de C1 ni de C2 y pueden hacerse antes si se prefiere cerrar primero la superficie de ataque.

## 5. Prompt para cada spec

Pegar en una sesión nueva de Claude Code, desde la carpeta del repo que corresponda, cambiando los datos entre corchetes:

```text
Leé CLAUDE.md, .claude/steering/*, .claude/specs/ROADMAP.md y la sección
"[ID del cambio, por ejemplo C2]" de ROADMAP-post-auditoria.md (en particular
las secciones 0 y 1: reglas y decisiones vigentes).

Ejecutá sdd-run para: [nombre de la spec] en este repo ([API | frontend]).

- Flujo completo: requirements.md, design.md y tasks.md, frenando para mi
  aprobación entre fases.
- Confirmá el número de spec en el ROADMAP.md del repo (siguiente libre, o
  enmienda NN-A si la spec ya existe).
- Verificá el estado real del código antes de asumir lo que dice el documento.
- Cada requisito con su estado de fallo explícito.
- No toques el otro repo ni migraciones ya aplicadas.
- Al cerrar: actualizá ROADMAP.md, README.md y CLAUDE.md; corré lint, tests y
  build; dejá en notes.md el orden de despliegue respecto del otro repo.
```

## 6. Fuera de los repos (infraestructura)

Tareas en `/opt/web-stack` del VPS. No son specs de ninguno de los dos repos, pero bloquean un despliegue correcto:

- Enrutar `/api` del dominio de la demo hacia el contenedor de la API (con o sin StripPrefix, según cómo se resuelva en F0).
- Definir en el servicio de la API: `JWT_SECRET`, `DB_PASSWORD`, `app.cors.allowed-origins` y, cuando corresponda, `BOOTSTRAP_ADMIN_*` y `app.guest.enabled`.
- Límites de memoria para la JVM y PostgreSQL (VPS de 2 vCPU y 2 GB de RAM): `mem_limit` en Compose coherente con `-XX:MaxRAMPercentage=60`.
- Confirmar que el script `front <tag>` / `api <tag>` conserva el rollback y que la clave de deploy solo puede ejecutar ese comando.

## 7. Backlog (sin prioridad asignada)

- Refresh token, logout con revocación y claims `iss`/`aud`/`jti` en el JWT.
- Umbral de cobertura bloqueante en CI.
- Internacionalización, tema oscuro, notificaciones en tiempo real.
- Exportación de órdenes y reportes.
- Adjuntos (fotos) en las órdenes.
