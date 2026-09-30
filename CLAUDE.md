# angular-enterprise-lab-api

Backend real (Java 21 + Spring Boot 3) de `angular-enterprise-lab`. Reemplaza
a JSON Server como fuente de datos y, sobre todo, mueve la autorización por
rol del cliente (hoy editable en localStorage) a un servidor con autoridad
real. Repo separado del frontend a propósito — ver `steering/product.md`.

## Metodología de desarrollo

Toda feature no trivial (más de ~3 archivos, o que toca datos/permisos/
integraciones) sigue la skill `anthropic-skills:sdd-flow`, flujo completo:
`requirements.md` → `design.md` → `tasks.md`, con aprobación explícita del
usuario entre cada fase. No avances de una fase a la siguiente sin esa
aprobación, aunque el pedido parezca claro.

- Specs en `.claude/specs/<número>-<nombre>/`, versionadas junto al código.
- Contexto de proyecto (qué es, stack, estructura) en `steering/` — leelo
  antes de escribir la primera spec de una sesión nueva.
- Si ya existe una spec para la feature que se está pidiendo, es una
  enmienda: se edita esa carpeta, nunca se crea una segunda para lo mismo.

## Arquitectura

- Organización vertical por feature (`auth/`, `workorders/`,
  `maintenance/`), cada una con sus propias capas `web/domain/persistence`
  — no carpetas `controllers/`, `services/`, `repositories/` a nivel raíz.
- `domain` no importa nada de JPA ni de Spring Data: los repositorios son
  interfaces ahí, la implementación vive en `persistence`. Si una clase de
  `domain` necesita una anotación de JPA, está mal ubicada.
- La autorización por rol se decide en `domain` (servicios), no en el
  controller ni en el filtro de seguridad — mismo criterio que
  `work-order.permissions.ts` del frontend, ahora con autoridad real.

## Convenciones técnicas

- Flyway para todo cambio de esquema (`V<n>__descripcion.sql`). Nunca
  `ddl-auto: update` ni scripts sueltos tipo `data.sql`.
- DTOs de request/response separados de las entities, con mapeo explícito.
- Errores controlados responden siempre el mismo formato JSON vía
  `@RestControllerAdvice` — nunca el stacktrace por defecto de Spring.
- Swagger/OpenAPI se genera desde anotaciones (springdoc), no se mantiene
  a mano.

## Estado actual

- Spec 00 (proyecto base y seguridad): `requirements.md`, `design.md` y
  `tasks.md` aprobados (2026-09-29). Las 22 tareas están implementadas y
  marcadas en `tasks.md`; los tests pasan con `./gradlew test`.
- Pendiente de verificación manual: seguir el README en un entorno limpio
  (tarea 21) y comprobar que el workflow de `.github/workflows/ci.yml`
  termine en verde en un PR de prueba (tarea 22).
- Implementado: login (`POST /auth/login`), `GET /auth/me`, JWT HS256,
  `AccessPolicy` (autorización por rol en `domain`), 401/403 en formato
  `ApiError`, CORS, OpenAPI (springdoc) y seed de usuarios en el perfil `dev`.
- Spec 01 (técnicos y equipos, módulo `maintenance`): `requirements.md`
  (REQ-1 a REQ-41), `design.md` y `tasks.md` aprobados (2026-09-30). Las 12
  tareas están implementadas y marcadas; `./gradlew test` pasa y el recorrido
  de REQ-1 a REQ-41 con su evidencia está al final de `tasks.md`. Implementado:
  `/technicians` y `/teams` (CRUD con permisos por rol en `domain`, validación
  en `domain` después de autorizar: orden `401 → 403 → 400 → 404 → 409`),
  migraciones `V2`/`V3` con el seed `V2_1` (solo `dev`) y las excepciones
  genéricas de `shared/domain`. Pendiente: que el workflow de CI termine en
  verde en un PR.
- Enmienda 00-A de la spec 00 (contrato de sesión del frontend): REQ-15 a
  REQ-24, diseño (`design.md` §10) y tareas 23 a 29 aprobados (2026-09-30), todas
  implementadas y marcadas; `./gradlew test` pasa y el recorrido de REQ-15 a
  REQ-24 con su evidencia está al final de `tasks.md` de la spec 00.
  Implementado: `POST /auth/login` devuelve `{token, user}`; `GET /auth/me`
  devuelve el mismo `user` leído de la base (`401` si el usuario del token ya no
  existe); el perfil del técnico (`specialty`, `teamType`) sale del maestro, no
  del token; migraciones `V4`/`V5` con el seed `V4_1` (solo `dev`). Pendiente:
  que el workflow de CI termine en verde en el PR.
- Spec 02 (máquinas y árbol de partes, módulo `machines`): `requirements.md`
  (REQ-1 a REQ-37), `design.md` y `tasks.md` aprobados (2026-09-30). Las 11
  tareas están implementadas y marcadas; `./gradlew test` pasa (589 tests) y el
  recorrido de REQ-1 a REQ-37 con su evidencia está al final de `tasks.md`.
  Implementado: `/machines` (con `partCount`) y las partes como lista plana con
  `parentId` (`GET`/`POST /machines/{machineId}/parts`, `PATCH`/`DELETE
  /parts/{id}`); lectura para los cuatro roles, escritura para administrador y
  team leader; orden de errores `401 → 403 → 400 formato → 404 → 400 referencia
  (padre) → 409`; FK compuesta que impide un padre de otra máquina; migración
  `V6` con el seed `V6_1` (solo `dev`, ids de `db.json`). `ConstraintViolations`
  pasó a `shared/persistence`. Pendiente: que el workflow de CI termine en verde
  en un PR.
- Spec 03 (órdenes de trabajo: alta, consulta, edición, baja y listado, módulo
  `workorders`): `requirements.md` (REQ-1 a REQ-46), `design.md` y `tasks.md`
  aprobados (2026-09-30). Las 14 tareas están implementadas y marcadas;
  `./gradlew test` pasa (908 tests) y el recorrido de REQ-1 a REQ-46 con su
  evidencia está al final de `tasks.md`. Implementado: `GET /work-orders`
  (paginado con `page`/`size`, respuesta `{data, page, size, totalItems,
  totalPages}`, filtros `title`, `status` y `priority`), `GET`/`PUT`/`DELETE
  /work-orders/{id}` y `POST /work-orders` (team leader crea `preventivo` y
  `correctivo`, producción `pronto-intervencion`). El servidor fija el estado
  inicial, el `createdAt` y el `breadcrumb` (una foto: sobrevive al renombre o la
  baja de la máquina o la parte; `work_orders` no tiene FK hacia `machines` ni
  `parts`). El `PUT` solo cambia título, descripción y prioridad, y rechaza cambiar
  el tipo o la máquina. Orden de errores `401 → 403 → 400 formato → 404 → 400
  referencia`; los parámetros de consulta se validan en `domain`. Migración `V7`
  con el seed `V7_1` (solo `dev`, generado desde `db.json`; las tres órdenes de id
  alfanumérico pasan a `30`, `31` y `32`). Las columnas de `takenBy` y
  `closingNote` ya existen pero esta spec solo las lee. `MachineIds` pasó a
  `shared/domain/NumericId`; `shared` suma `PageQuery`, `PageResult`,
  `PageResponse` y `ClockConfig`. Pendiente: que el workflow de CI termine en
  verde en un PR.
- Próximo paso: el mapa de specs está en `.claude/specs/ROADMAP.md` (04 órdenes:
  tomar, cerrar y liberar; 05 dashboard; 06 operación). Contrato de API decidido:
  REST limpio en inglés. Los `requirements.md` se escriben de a una spec, con
  aprobación entre cada una. **La spec 04 no debería necesitar migrar** (las
  columnas de `takenBy` y `closingNote` ya están en `V7`; si lo necesita,
  numera desde `V8`). Migraciones: la 01 usa `V2`, `V2_1` y `V3`; la 00-A usa `V4`,
  `V4_1` y `V5`; la 02 usa `V6` y `V6_1`; la 03 usa `V7` y `V7_1`. Pendiente de
  la spec 00: su cierre formal (REQ-1 a REQ-14).
- Entorno: compilar requiere JDK 21 (`JAVA_HOME`) y Docker para las IT.

## Testing

- JUnit 5 + Mockito para unitarios (mock de las interfaces de `domain`).
- `@SpringBootTest` + Testcontainers (Postgres real) para integración.
- Cada tarea de `tasks.md` referencia qué test la verifica.

## Instrucciones de compactación

Al compactar, preservar: spec activa (requirements/design/tasks), fase en
la que está (qué aprobación falta), archivos modificados, decisiones de
arquitectura tomadas. Descartar: exploración descartada, logs de comandos
repetidos.
