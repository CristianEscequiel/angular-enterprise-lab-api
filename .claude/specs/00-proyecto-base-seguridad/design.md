# Spec 00 — Diseño: proyecto base y seguridad

## 1. Decisiones técnicas

| Tema | Decisión | Motivo |
|---|---|---|
| Build | Gradle **Kotlin DSL** (`build.gradle.kts`), Java 21 toolchain, Spring Boot 3.x | Tipado y autocompletado en el IDE; es la opción por defecto de Spring Initializr moderno |
| JWT | `spring-boot-starter-oauth2-resource-server`: `NimbusJwtEncoder` para emitir y `NimbusJwtDecoder` para validar, con **HS256** y secreto simétrico | Evita un `JwtFilter` a mano: la validación de firma, la expiración y el formato los hace Spring Security, que resuelve REQ-10 sin código propio. Sin dependencia extra (JJWT) |
| Hash | `BCryptPasswordEncoder` (strength 10) expuesto como bean `PasswordEncoder` | REQ-6 |
| Mapeo | **Mappers manuales**, sin MapStruct | Hay pocos tipos en esta spec. Se reevalúa en la spec de work orders |
| Migraciones | Flyway. El esquema va en `db/migration` y el seed en `db/seed`, que solo se incluye en el perfil `dev` | Los usuarios de prueba no deben llegar a un entorno productivo |
| Errores | `@RestControllerAdvice` en `shared/web` + `AuthenticationEntryPoint`/`AccessDeniedHandler` propios que escriben el mismo JSON | Los 401/403 del filtro de seguridad no pasan por el advice, así que hacen falta ambos para cumplir REQ-13 |
| OpenAPI | `springdoc-openapi-starter-webmvc-ui` + esquema de seguridad `bearerAuth` | REQ-14 |

> Desvío respecto de `structure.md`: allí figura `auth/security/JwtFilter`. Con resource-server ese filtro lo provee Spring (`BearerTokenAuthenticationFilter`), así que `auth/security` contiene `SecurityConfig`, `JwtConfig` y `JwtTokenIssuer`.

## 2. Resolución del punto abierto (legajo / technicians)

**Decisión:** `V1__init.sql` crea `users` y una tabla `technicians` **mínima**. Se descarta `teams` en esta spec: nada de los requisitos la necesita. Esto aclara REQ-4.

- `technicians(id, legajo)`, con `legajo` único y not null. La spec de mantenimiento la extiende con `ALTER TABLE` (especialidad, tipo de equipo, etc.).
- `users.technician_id` es una FK nullable a `technicians`.
- Un `CHECK` a nivel de base de datos garantiza REQ-5: `(role = 'tecnico') = (technician_id IS NOT NULL)`.

Así el usuario `tecnico` del seed nace válido, y la referencia a legajo es relacional en lugar de un string duplicado.

## 3. Modelo de datos

```sql
-- V1__init.sql
CREATE TABLE technicians (
  id      BIGSERIAL PRIMARY KEY,
  legajo  VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE users (
  id             BIGSERIAL PRIMARY KEY,
  username       VARCHAR(50)  NOT NULL UNIQUE,
  password_hash  VARCHAR(100) NOT NULL,
  role           VARCHAR(40)  NOT NULL
                 CHECK (role IN ('administrador','team-leader-mantenimiento',
                                 'personal-produccion','tecnico')),
  technician_id  BIGINT REFERENCES technicians(id),
  CONSTRAINT users_tecnico_has_legajo
    CHECK ((role = 'tecnico') = (technician_id IS NOT NULL))
);
```

- `db/seed/V1_1__seed_users.sql` (solo en el perfil `dev`) carga un usuario por rol, replicando los de `db.json` del frontend. Las contraseñas van como hash BCrypt precalculado y la contraseña en claro se documenta en el README.
- Dominio: `User(id, username, passwordHash, Role role, String legajo)`, más el enum `Role`, que mapea cada valor al string kebab-case (`Role.fromValue`). El dominio no usa anotaciones JPA.

## 4. Componentes por capa

```
com.enterpriselab.api
├── ApiApplication
├── shared/web/        ApiError (record), RestExceptionHandler, ErrorResponseWriter
├── auth/web/          AuthController, LoginRequest, LoginResponse, MeResponse
├── auth/domain/       User, Role, UserRepository (puerto), AuthService,
│                      InvalidCredentialsException, AccessPolicy
├── auth/persistence/  UserEntity, TechnicianEntity, UserJpaRepository,
│                      UserRepositoryAdapter, UserMapper
└── auth/security/     SecurityConfig, JwtConfig, JwtTokenIssuer, JwtProperties,
                       CorsProperties, JsonAuthenticationEntryPoint,
                       JsonAccessDeniedHandler
```

- **`AuthService.login(username, password)`**
  - Busca el usuario por el puerto `UserRepository`.
  - Si no existe, igual ejecuta `passwordEncoder.matches` contra un hash dummy para igualar tiempos y no filtrar qué usuarios existen (REQ-8).
  - Si falla, lanza `InvalidCredentialsException`, con el mismo mensaje en ambos casos.
- **`JwtTokenIssuer`** firma un token con estos claims:
  - `sub = username`
  - `role`
  - `legajo` (solo si el rol es `tecnico`)
  - `iat` y `exp`, con TTL configurable (por defecto 60 min)

  (REQ-7)
- **`JwtConfig`**
  - Define el `JwtEncoder`/`JwtDecoder` con la clave HMAC tomada de `app.jwt.secret`.
  - Define un `JwtAuthenticationConverter` que transforma el claim `role` en la autoridad `ROLE_<valor>`.
- **Autorización por rol (REQ-11):** siguiendo `structure.md`, vive en el dominio.
  - `AccessPolicy.requireRole(Role... allowed)` recibe el rol actual, que lo resuelve la capa web desde el `Jwt`.
  - Si el rol no está permitido, lanza la excepción de dominio `ForbiddenOperationException`, que el advice traduce a 403.
  - `SecurityConfig` solo decide qué rutas son públicas y cuáles requieren autenticación.

## 5. Contrato HTTP

| Método y ruta | Acceso | Respuesta |
|---|---|---|
| `GET /actuator/health` | público | `200 {"status":"UP"}`, con `show-details: never` (REQ-1) |
| `POST /auth/login` | público | `200 {"token","tokenType":"Bearer","expiresIn"}`; 400 si falla la validación; 401 si las credenciales son inválidas |
| `GET /auth/me` | autenticado | `200 {"username","role","legajo"}`. Es el endpoint protegido real que demuestra REQ-9 y REQ-10, y le sirve al frontend para rehidratar la sesión |
| `/swagger-ui.html`, `/v3/api-docs/**` | público | REQ-14 |

REQ-11 se verifica con un controller de test (`@TestConfiguration`, solo en `src/test`) restringido a `administrador` vía `AccessPolicy`. Así no se publica un endpoint artificial. Las specs siguientes aportan los endpoints restringidos reales.

**Cuerpo de error (REQ-13)**, igual para todos los errores controlados:
```json
{ "code": "UNAUTHORIZED", "message": "Credenciales inválidas", "timestamp": "2026-09-29T12:00:00Z", "path": "/auth/login" }
```

| Código | HTTP | Cuándo |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Falla Bean Validation (incluye un `details` opcional por campo) |
| `UNAUTHORIZED` | 401 | Login fallido, token ausente o token inválido; el mensaje es genérico (REQ-8, REQ-9 y REQ-10) |
| `FORBIDDEN` | 403 | El rol no está permitido (REQ-11) |
| `NOT_FOUND` | 404 | `NoResourceFoundException` o recurso inexistente |
| `INTERNAL_ERROR` | 500 | Excepción no controlada: se loguea y el cliente recibe un mensaje genérico, sin stacktrace |

Además, `server.error.include-stacktrace: never`.

## 6. Configuración

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/enterpriselab
    username: enterpriselab
    password: ${DB_PASSWORD:enterpriselab}
  jpa.hibernate.ddl-auto: validate
  flyway.locations: classpath:db/migration
app:
  jwt:
    secret: ${JWT_SECRET}          # ≥ 32 bytes; sin default en prod
    ttl: 60m
  cors:
    allowed-origins: [http://localhost:4200]
---
spring.config.activate.on-profile: dev
spring.flyway.locations: classpath:db/migration,classpath:db/seed
app.jwt.secret: ${JWT_SECRET:dev-only-secret-change-me-32-bytes-min}
```

- **CORS (REQ-12):** `CorsConfigurationSource` construido desde `CorsProperties`.
  - Métodos: GET, POST, PUT, PATCH y DELETE.
  - Headers permitidos: `Authorization` y `Content-Type`.
  - Si la lista de orígenes contiene `*`, la aplicación falla al arrancar (validación en `@ConfigurationProperties`).
- **Fail fast (REQ-3):** Flyway corre al inicio y Hikari usa el valor por defecto `initializationFailTimeout=1`. Sin base de datos, el contexto no levanta y el log muestra `Connection refused` de PostgreSQL. No hay reintentos silenciosos.
- **`ddl-auto: validate`:** si una entity diverge del esquema de Flyway, el arranque también falla.
- **`docker-compose.yaml` (REQ-2):**
  - servicio `postgres:16-alpine` con el puerto 5432 expuesto,
  - las mismas credenciales que `application.yml`,
  - un volumen con nombre,
  - un healthcheck con `pg_isready`.

## 7. Estrategia de pruebas

| Requisito | Prueba |
|---|---|
| REQ-1 | `HealthIT`: `GET /actuator/health` devuelve 200 con `UP` |
| REQ-2 | Manual: `docker compose up` seguido de `./gradlew bootRun --args='--spring.profiles.active=dev'` |
| REQ-3 | `StartupFailureIT`: `SpringApplication` apuntando a un puerto cerrado lanza una excepción cuya causa contiene `Connection` |
| REQ-4, REQ-5 | `MigrationIT` (Testcontainers):<br>• las tablas existen;<br>• insertar un `tecnico` sin `technician_id` viola el CHECK;<br>• insertar un rol inválido viola el CHECK |
| REQ-6 | `AuthServiceTest` (Mockito) + `MigrationIT`: el `password_hash` del seed empieza con `$2a$`/`$2b$` |
| REQ-7 | `AuthControllerIT`: login OK y el token decodificado trae `sub`, `role` y `legajo` |
| REQ-8 | `AuthControllerIT`: usuario inexistente y contraseña incorrecta devuelven el **mismo** cuerpo 401 |
| REQ-9, REQ-10 | `AuthControllerIT`: `/auth/me` sin token, con token expirado (TTL negativo), mal firmado o `abc` da 401 con el mismo `message` |
| REQ-11 | `AccessPolicyTest` + `RoleRestrictionIT` con el controller de test: `tecnico` recibe 403 y `administrador` recibe 200 |
| REQ-12 | `CorsIT`: preflight desde `localhost:4200` incluye `Access-Control-Allow-Origin`; desde `evil.com` no |
| REQ-13 | Cubierto por las IT anteriores: se verifica la forma `{code,message,timestamp,path}` |
| REQ-14 | `OpenApiIT`: `/v3/api-docs` contiene `/auth/login` y `/auth/me` |

Las IT extienden una base común `AbstractPostgresIT` con `@Testcontainers` y `@ServiceConnection`, que comparte un contenedor para todas.

## 8. Decisiones pendientes (fuera de esta spec)

- **Refresh tokens / rotación:** hoy el token es de corta duración y sin revocación. Opciones futuras:
  - refresh token opaco guardado en base de datos, con rotación, o
  - migrar a un Authorization Server.

  Implica que el logout es solo del lado del cliente.
- **Clave JWT:** si en algún momento otro servicio valida tokens, conviene pasar de HS256 a RS256 con un par de claves.
- **Tabla `teams`:** se crea en la spec de mantenimiento.
- **Alta de usuarios:** cuando exista el alta, debe usar el mismo bean `PasswordEncoder` (REQ-6).

## 9. Trazabilidad

| REQ | Sección |
|---|---|
| 1 | §5, §6 |
| 2 | §6 |
| 3 | §6 |
| 4 | §2, §3 |
| 5 | §2, §3 |
| 6 | §1, §3 |
| 7 | §4, §5 |
| 8 | §4, §5 |
| 9 | §5 |
| 10 | §1, §5 |
| 11 | §4, §5 |
| 12 | §6 |
| 13 | §1, §5 |
| 14 | §1, §5 |
