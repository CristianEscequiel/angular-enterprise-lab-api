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
   de usuarios de `db/seed` y usa un secreto JWT por defecto solo para dev):

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

## Probar el login

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/auth/me
```

## Endpoints

| Endpoint             | Acceso      | Descripción                                   |
| -------------------- | ----------- | --------------------------------------------- |
| `GET /actuator/health` | público   | Estado de la aplicación                       |
| `POST /auth/login`   | público     | Devuelve un JWT (HS256)                       |
| `GET /auth/me`       | autenticado | `username`, `role` y `legajo` del token       |
| `/swagger-ui.html`, `/v3/api-docs/**` | público | Documentación OpenAPI          |

Los errores controlados responden siempre `{code, message, timestamp, path}`.

## Tests

```bash
./gradlew test
```

Las pruebas de integración levantan un PostgreSQL real con Testcontainers,
así que Docker debe estar corriendo.
