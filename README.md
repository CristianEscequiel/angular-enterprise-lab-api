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
| `/swagger-ui.html`, `/v3/api-docs/**` | público | Documentación OpenAPI          |

Los errores controlados responden siempre `{code, message, timestamp, path}`.

## Tests

```bash
./gradlew test
```

Las pruebas de integración levantan un PostgreSQL real con Testcontainers,
así que Docker debe estar corriendo.
