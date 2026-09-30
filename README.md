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

| Usuario        | Contraseña        | Rol                         | Legajo |
| -------------- | ----------------- | --------------------------- | ------ |
| `admin`        | `admin123`        | `administrador`             | —      |
| `teamleader`   | `teamleader123`   | `team-leader-mantenimiento` | —      |
| `produccion`   | `produccion123`   | `personal-produccion`       | —      |
| `tecnico`      | `tecnico123`      | `tecnico`                   | 1001   |
| `electricista` | `electricista123` | `tecnico`                   | 1002   |

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
| `POST /auth/login`   | público     | Devuelve un JWT (HS256)                       |
| `GET /auth/me`       | autenticado | `username`, `role` y `legajo` del token       |
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
