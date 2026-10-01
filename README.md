# angular-enterprise-lab-api

Backend (Java 21 + Spring Boot 3) de `angular-enterprise-lab`: autenticación
con JWT y autorización por rol con autoridad real en el servidor. Contexto y
decisiones de diseño en `.claude/steering/` y `.claude/specs/`.

## Requisitos

- JDK 21
- Docker (PostgreSQL local y tests de integración con Testcontainers)

## Levantar el proyecto

1. Base de datos:

   ```bash
   docker compose up -d
   docker compose ps   # el servicio postgres debe figurar como "healthy"
   ```

2. API con el perfil `dev` (aplica las migraciones de `db/migration`, el seed
   de usuarios, técnicos y equipos de `db/seed` y usa un secreto JWT por
   defecto solo para dev):

   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```

   Fuera del perfil `dev` hay que definir `JWT_SECRET` (mínimo 32 bytes); sin
   él la aplicación no arranca. `DB_PASSWORD` es opcional (por defecto
   `enterpriselab`).

3. Comprobar:

   - Health: <http://localhost:8080/actuator/health> → `{"status":"UP"}`
   - Swagger UI: <http://localhost:8080/swagger-ui.html>
   - OpenAPI JSON: <http://localhost:8080/v3/api-docs>

## Usuarios de desarrollo (seed)

Solo existen con el perfil `dev`. Replican los del `db.json` del frontend.

| Usuario        | Contraseña        | Rol                         | Legajo | Nombre visible                  | Correo                           |
| -------------- | ----------------- | --------------------------- | ------ | ------------------------------- | -------------------------------- |
| `admin`        | `admin123`        | `administrador`             | —      | Administrador                   | `admin@enterprise-lab.dev`       |
| `teamleader`   | `teamleader123`   | `team-leader-mantenimiento` | —      | Team Leader de Mantenimiento    | `teamleader@enterprise-lab.dev`  |
| `produccion`   | `produccion123`   | `personal-produccion`       | —      | Personal de Producción          | `produccion@enterprise-lab.dev`  |
| `tecnico`      | `tecnico123`      | `tecnico`                   | 1001   | Técnico Mecánico de Guardia     | `tecnico@enterprise-lab.dev`     |
| `electricista` | `electricista123` | `tecnico`                   | 1002   | Técnico Electricista Preventivo | `electricista@enterprise-lab.dev`|

Si ya tenías la base de la spec 01, al arrancar con `dev` se completan el nombre
visible y el correo de estos usuarios sobre las mismas filas (conservan su `id`,
su contraseña y su legajo).

## Técnicos y equipos de desarrollo (seed)

También solo con el perfil `dev`, igual que el `db.json` del frontend. Si ya
tenías la base de la spec 00, la próxima vez que arranques con `dev` se
completan `1001` y `1002` sobre las mismas filas (los usuarios `tecnico` y
`electricista` siguen asociados) y se agregan `1003` y los dos equipos.

| Legajo | Nombre     | Especialidad   | Tipo de equipo          | Usuario de login |
| ------ | ---------- | -------------- | ----------------------- | ---------------- |
| 1001   | Ana Ruiz   | `mecanico`     | `guardia`               | `tecnico`        |
| 1002   | Luis Paz   | `electricista` | `preventivo-correctivo` | `electricista`   |
| 1003   | Marta Gómez | `general`     | `preventivo-correctivo` | —                |

| Equipo                | Tipo                    | Miembros |
| --------------------- | ----------------------- | -------- |
| Guardia mecánica      | `guardia`               | 1001     |
| Preventivo eléctrico  | `preventivo-correctivo` | 1002     |

## Máquinas y partes de desarrollo (seed)

También solo con el perfil `dev`, con los mismos ids que el `db.json` del frontend
(las órdenes de prueba de la spec 03 los referencian). Los ids nuevos siguen
después del mayor: la próxima máquina es la `4` y la próxima parte, la `11`.

| Id | Código   | Nombre              | Partes |
| -- | -------- | ------------------- | ------ |
| 1  | `ENV-01` | Envasadora línea 1  | 7      |
| 2  | `SEL-02` | Selladora           | 3      |
| 3  | `ROT-03` | Rotuladora          | 0      |

Árbol de partes (el número es el id):

```
ENV-01  1 Mesa de transporte
          ├─ 2 Cinta 1 ── 3 Motor de cinta ── 4 Rodamiento delantero
          └─ 5 Cinta 2
        6 Cabezal de sellado ── 7 Resistencia
SEL-02  8 Cabezal térmico ── 9 Resistencia
        10 Mordaza
```

## Órdenes de trabajo de desarrollo (seed)

También solo con el perfil `dev`: las 32 órdenes del `db.json` del frontend (12
`pending`, 9 `in-progress`, 9 `completed` y 2 `cancelled`), con sus `machineRef`,
dueños y notas de cierre. Tres detalles del seed:

- Las órdenes `1` a `29` conservan su id. Las tres que en `db.json` tienen un id
  alfanumérico (`jgFCUkYKm4M`, `dW8mYm5vbQs` y `53mjVg8IKEk`, todas `pending`)
  pasan a ser la `30`, la `31` y la `32`, porque los ids de las órdenes son
  numéricos. La próxima orden que se cree es la `33`.
- Los `createdAt` que `db.json` guarda sin zona horaria se interpretan como UTC.
- Los dueños y autores de cierre `2` y `5` del frontend quedan a nombre de los
  usuarios `tecnico` y `electricista` (los ids de usuario del backend no
  coinciden con los del frontend); los nombres se conservan.

## Probar el login

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/auth/me
```

El login responde `{token, user}` y `GET /auth/me` devuelve ese mismo `user`, leído
de la base en cada llamada. Para el técnico trae también su `legajo`, `specialty` y
`teamType` (del maestro de técnicos); para el resto de los roles esas tres claves
no aparecen:

```json
{"token": "eyJ...", "user": {"id": "4", "username": "tecnico",
  "displayName": "Técnico Mecánico de Guardia", "email": "tecnico@enterprise-lab.dev",
  "role": "tecnico", "legajo": "1001", "specialty": "mecanico", "teamType": "guardia"}}
```

Los `id` de los usuarios salen de la secuencia de Postgres, así que no coinciden con
los del `db.json` del frontend.

Para técnicos y equipos, con el usuario que corresponda (`admin` o
`teamleader` para `/technicians`; solo `teamleader` para `/teams`):

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"teamleader","password":"teamleader123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/technicians
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/teams
```

Máquinas y partes: cualquier usuario autenticado puede leer; escribir requiere
`admin` o `teamleader`. Las partes de una máquina se piden y se crean por su
máquina, y se editan y eliminan por su id:

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/machines
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/machines/1/parts

curl -X POST http://localhost:8080/machines/1/parts \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Tensor","parentId":"1"}'

curl -X PATCH http://localhost:8080/parts/11 \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Tensor de cinta"}'
```

Órdenes de trabajo: cualquier usuario autenticado puede leer. Crear requiere
`teamleader` (tipos `preventivo` y `correctivo`) o `produccion` (tipo
`pronto-intervencion`); editar, `admin` o `teamleader`; eliminar, solo `admin`. El
listado se pagina y se filtra por título, estado y prioridad:

```bash
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/work-orders"
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/work-orders?page=2&size=5&title=motor&status=pending&priority=high"

curl -X POST http://localhost:8080/work-orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Revisar motor","description":"Vibración fuera de rango",
       "type":"correctivo","priority":"high",
       "machineRef":{"machineId":"1","partId":"3","comment":"Hace ruido"}}'
```

La respuesta del listado es `{data, page, size, totalItems, totalPages}`. El
servidor fija el estado inicial (`pending`), el `createdAt` y el `breadcrumb` (la
ruta `máquina > parte > sub-parte`, que es una foto: no cambia si después se
renombra o elimina la máquina o la parte).

### Tomar, cerrar y liberar una orden

El dueño sale del token, nunca del cuerpo. Un técnico toma solo órdenes que su
equipo atiende (`tecnico`, de guardia: `pronto-intervencion`; `electricista`,
preventivo-correctivo: `preventivo` y `correctivo`):

```bash
curl -X POST http://localhost:8080/work-orders/7/take -H "Authorization: Bearer $TECNICO"

curl -X POST http://localhost:8080/work-orders/7/close \
  -H "Authorization: Bearer $TECNICO" -H 'Content-Type: application/json' \
  -d '{"outcome":"completed","comment":"Se reemplazó el rodamiento y se verificó el funcionamiento."}'

# administrador o team leader devuelven una orden en progreso a pending
curl -X POST http://localhost:8080/work-orders/7/release -H "Authorization: Bearer $ADMIN"
```

Un conflicto de estado responde `409` con el estado real y, si hay dueño, quién es:

```json
{ "code": "WORK_ORDER_NOT_PENDING", "message": "La orden 7 no está pendiente",
  "timestamp": "2026-09-30T12:00:00Z", "path": "/work-orders/7/take",
  "details": { "status": "in-progress", "takenById": "5", "takenByName": "Técnico Electricista Preventivo" } }
```

Los otros códigos son `WORK_ORDER_NOT_IN_PROGRESS` y `WORK_ORDER_TAKEN_BY_OTHER`.
Una orden cerrada no se reabre.

## Endpoints

| Endpoint             | Acceso      | Descripción                                   |
| -------------------- | ----------- | --------------------------------------------- |
| `GET /actuator/health` | público   | Estado de la aplicación                       |
| `POST /auth/login`   | público     | Devuelve `{token, user}` (JWT HS256 y el usuario completo) |
| `GET /auth/me`       | autenticado | El mismo `user` del login, leído de la base; `401` si el usuario del token ya no existe |
| `GET /technicians`, `GET /technicians/{legajo}` | administrador, team leader | Consulta de técnicos (por legajo) |
| `POST /technicians`, `PUT /technicians/{legajo}` | administrador, team leader | Alta y edición (el legajo no se edita) |
| `DELETE /technicians/{legajo}` | administrador | Baja; `409` si tiene login o es miembro de un equipo |
| `GET`, `POST /teams`, `GET`, `PUT`, `DELETE /teams/{id}` | team leader | Equipos y sus miembros (por legajo) |
| `GET /machines`, `GET /machines/{id}` | autenticado | Consulta de máquinas, con `partCount` |
| `POST /machines`, `PUT /machines/{id}` | administrador, team leader | Alta y edición; el `code` se guarda recortado y en mayúsculas; `409 DUPLICATE_MACHINE_CODE` si ya existe |
| `DELETE /machines/{id}` | administrador, team leader | Baja; `409 MACHINE_HAS_PARTS` si tiene partes |
| `GET /machines/{machineId}/parts` | autenticado | Partes de la máquina como lista plana con `parentId`, en orden de creación |
| `POST /machines/{machineId}/parts` | administrador, team leader | Alta de una parte de primer nivel o sub-parte; `400 PARENT_PART_NOT_FOUND` / `PARENT_PART_OTHER_MACHINE` |
| `PATCH /parts/{id}` | administrador, team leader | Cambia solo el nombre; `400` si se intenta mover (`machineId` o `parentId` distintos) |
| `DELETE /parts/{id}` | administrador, team leader | Baja; `409 PART_HAS_CHILDREN` si tiene sub-partes |
| `GET /work-orders` | autenticado | Listado paginado (`page`, `size`, máx. 100) con filtros opcionales `title`, `status` y `priority`; `400` si un parámetro es inválido |
| `GET /work-orders/{id}` | autenticado | Una orden completa, con `machineRef`, `takenBy` y `closingNote` si existen |
| `POST /work-orders` | team leader (`preventivo`, `correctivo`), producción (`pronto-intervencion`) | Alta; `400` con `MACHINE_NOT_FOUND`, `PART_NOT_FOUND` o `PART_OTHER_MACHINE` si la referencia no resuelve |
| `PUT /work-orders/{id}` | administrador, team leader | Edita título, descripción y prioridad, en cualquier estado; `400` si se intenta cambiar el tipo o la máquina |
| `DELETE /work-orders/{id}` | administrador | Baja, en cualquier estado |
| `POST /work-orders/{id}/take` | técnico de equipo habilitado | Toma una orden `pending` (pasa a `in-progress` a su nombre); `403` si su equipo no atiende el tipo; `409 WORK_ORDER_NOT_PENDING` |
| `POST /work-orders/{id}/close` | técnico dueño de la orden | Cierra con `{outcome: `completed` o `cancelled`, comment}` (50 a 500 caracteres); `400`, `409 WORK_ORDER_NOT_IN_PROGRESS` o `WORK_ORDER_TAKEN_BY_OTHER` |
| `POST /work-orders/{id}/release` | administrador, team leader | Devuelve una orden `in-progress` a `pending` sin dueño; `409 WORK_ORDER_NOT_IN_PROGRESS` |
| `/swagger-ui.html`, `/v3/api-docs/**` | público | Documentación OpenAPI          |

Los errores controlados responden siempre `{code, message, timestamp, path}` (y `details` en `400` de validación y en los `409` de las transiciones de una orden).

## Tests

```bash
./gradlew test
```

Las pruebas de integración levantan un PostgreSQL real con Testcontainers,
así que Docker debe estar corriendo.
