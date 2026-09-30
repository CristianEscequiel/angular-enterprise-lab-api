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
| 15 a 24 | §10 (enmienda 00-A) |

## 10. Enmienda 00-A — sesión completa del frontend (REQ-15 a REQ-24)

Se agrega acá en vez de abrir una spec nueva (regla de `CLAUDE.md`). **Amplía** §3, §4 y §5 sin borrarlos: donde esta sección contradice a una anterior, manda esta. Nace de `auth.model.ts` del frontend, que espera `{token, user}` y valida `user` de forma estricta (`isAuthUser`: un técnico sin sus tres atributos, o un no-técnico con alguno de ellos presente, invalida la sesión).

### 10.1 Decisiones técnicas

| Tema | Decisión | Motivo |
|---|---|---|
| Objeto de sesión | `User` (dominio) suma `displayName`, `email`, `specialty` y `teamType`; los tres últimos campos de perfil son `null` salvo en el técnico. La capa web arma un `UserResponse` con los campos **públicos** y nunca expone `passwordHash` | REQ-22. Una sola clase de dominio alcanza: el hash nunca sale porque `UserResponse` no lo tiene |
| `specialty` y `teamType` en `auth.domain` | `String` (su valor kebab-case), no los enums de `maintenance.domain` | `maintenance.domain` ya importa `auth.domain` (`Role`, `AccessPolicy`); que `auth.domain` importe `maintenance.domain` cerraría un ciclo entre los dos dominios. Auth solo transporta esos valores, no tiene reglas sobre ellos |
| De dónde sale el perfil | `UserMapper` lo lee de `TechnicianEntity` (la asociación `users.technician_id` que ya existe), dentro de la transacción de lectura de `UserRepositoryAdapter` | No hay consulta nueva ni dependencia nueva entre módulos: `auth.persistence` ya importa esa entity. La fuente de verdad sigue siendo el maestro (REQ-18, REQ-21) |
| Resultado del login | `AuthService.login` devuelve un record de dominio `AuthSession(String token, User user)` en lugar de un `String` | El controller arma `{token, user}` sin volver a consultar la base |
| `GET /auth/me` | Nuevo `AuthService.currentUser(username)`: busca por el `sub` del token y arma el mismo `UserResponse`. Lee de la base en cada llamada (REQ-20, REQ-21); no usa los claims | El token no lleva el perfil (REQ-23) y puede quedar viejo |
| Usuario inexistente en `/me` | `UnknownSessionUserException` (dominio), que `RestExceptionHandler` traduce a `401 UNAUTHORIZED` con **el mismo mensaje** que el entry point: `JsonAuthenticationEntryPoint.MESSAGE` pasa a `public` y el advice lo reutiliza | REQ-24. Un mismo texto evita distinguir "token inválido" de "usuario borrado". Lanzar una `AuthenticationException` desde el controller no sirve: el handler genérico de `Exception` la convertiría en `500` |
| Rol en `/me` | Sale de la base; la autorización (`AccessPolicy`) sigue usando el rol **del token** | Hoy ningún endpoint cambia roles, así que no divergen. Si alguno aparece, habrá que decidir si invalida los tokens (spec de refresh/rotación, §8) |
| Claims del JWT | Sin cambios: `JwtTokenIssuer` no agrega `specialty` ni `teamType` | REQ-23; solo se suma un test que lo fija |
| Forma de `/me` | `MeResponse` desaparece y `UserResponse` la reemplaza | Sin consumidores todavía (frontend usa `localStorage`); no se mantiene compatibilidad |

### 10.2 Modelo de datos y migraciones

```sql
-- V4__users_profile.sql  (db/migration)
ALTER TABLE users
    ADD COLUMN display_name VARCHAR(100),
    ADD COLUMN email        VARCHAR(254);

-- V4_1__seed_users_profile.sql  (db/seed, solo dev)
UPDATE users SET display_name = 'Administrador', email = 'admin@enterprise-lab.dev' WHERE username = 'admin';
-- ... y los otros cuatro usuarios, con los valores de db.json (ver tabla)

-- V5__users_profile_required.sql  (db/migration)
ALTER TABLE users
    ALTER COLUMN display_name SET NOT NULL,
    ALTER COLUMN email        SET NOT NULL,
    ADD CONSTRAINT users_profile_not_blank
        CHECK (btrim(display_name) <> '' AND btrim(email) <> '');
```

Mismo problema y misma solución que la spec 01 (§2.2 de su diseño): las columnas nacen nulables para convivir con los usuarios que `V1_1` ya sembró; el seed las completa con `UPDATE` (conserva los `id`, las contraseñas y el vínculo con el técnico) y recién después se vuelven obligatorias.

| Perfil | Migraciones, en orden |
|---|---|
| `dev`, base nueva | `V1` → `V1_1` → `V2` → `V2_1` → `V3` → **`V4` → `V4_1` → `V5`** |
| `dev`, base ya migrada hasta la spec 01 | Pendientes: `V4` → `V4_1` → `V5` |
| resto de los perfiles | `V1` → `V2` → `V3` → `V4` → `V5` (sin filas) |

- **Numeración:** la spec 01 usa `V2`, `V2_1` y `V3`; esta usa `V4`, `V4_1` y `V5`. La spec 02 (máquinas) debe numerar desde `V6`; si se implementa antes, quien llegue segundo toma los siguientes libres.
- **Si una base no-dev tuviera usuarios sin perfil, `V5` falla al arrancar** (fail-fast buscado). Hoy no hay forma de crear usuarios sin pasar por el seed.
- **Sin `UNIQUE` en `email`:** ningún requisito lo pide y no hay endpoint que cree usuarios.
- **Desvío de REQ-15:** ya dice "las migraciones" (ajustado al aprobar los requisitos).

Datos de prueba (REQ-16), copiados de `db.json`; el `id` **no** coincide con el del frontend, porque sale de la secuencia de Postgres en el orden de `V1_1`:

| `username` | `id` | `displayName` | `email` |
|---|---|---|---|
| `admin` | `"1"` | Administrador | `admin@enterprise-lab.dev` |
| `teamleader` | `"2"` | Team Leader de Mantenimiento | `teamleader@enterprise-lab.dev` |
| `produccion` | `"3"` | Personal de Producción | `produccion@enterprise-lab.dev` |
| `tecnico` | `"4"` | Técnico Mecánico de Guardia | `tecnico@enterprise-lab.dev` |
| `electricista` | `"5"` | Técnico Electricista Preventivo | `electricista@enterprise-lab.dev` |

### 10.3 Componentes afectados

```
auth/domain/       User (+displayName, email, specialty, teamType y sus invariantes),
                   AuthSession (nuevo), AuthService (login → AuthSession; currentUser),
                   UnknownSessionUserException (nueva)
auth/persistence/  UserEntity (+display_name, email), UserMapper (perfil desde TechnicianEntity)
auth/web/          LoginResponse {token, user}, UserResponse (nuevo, reemplaza a MeResponse),
                   AuthController (login y /me)
auth/security/     JsonAuthenticationEntryPoint (MESSAGE pasa a public); JwtTokenIssuer sin cambios
shared/web/        RestExceptionHandler (+ handler de UnknownSessionUserException)
```

- **Invariantes de `User`** (se suman a las de REQ-5 de la spec 00): `displayName` y `email` no nulos ni en blanco; un `tecnico` tiene `specialty` y `teamType`; un no-técnico no los tiene.
- **`UserResponse(id, username, displayName, email, role, legajo, specialty, teamType)`**, con `@JsonInclude(NON_NULL)`: para un no-técnico los tres últimos se **omiten** (REQ-19), no van en `null`, que es lo que exige `isAuthUser` del frontend. `id` es `String.valueOf(user.id())`.
- `LoginResponse(String token, UserResponse user)`.

### 10.4 Contrato HTTP

| Método y ruta | Respuesta |
|---|---|
| `POST /auth/login` | `200 {"token": "...", "user": {...}}`; 400 y 401 como antes (REQ-8) |
| `GET /auth/me` | `200` con el mismo `user` que el login, leído de la base; `401` como antes para token ausente o inválido, y también (REQ-24) si el usuario del token ya no existe |

```json
{ "token": "eyJ...", "user": { "id": "4", "username": "tecnico", "displayName": "Técnico Mecánico de Guardia",
  "email": "tecnico@enterprise-lab.dev", "role": "tecnico", "legajo": "1001",
  "specialty": "mecanico", "teamType": "guardia" } }
```

```json
{ "id": "1", "username": "admin", "displayName": "Administrador", "email": "admin@enterprise-lab.dev", "role": "administrador" }
```

### 10.5 Impacto sobre lo ya construido

- **Código:** `LoginResponse`, `MeResponse` (se elimina), `AuthController`, `AuthService`, `User`, `UserMapper`, `UserEntity`, `JsonAuthenticationEntryPoint` (solo visibilidad) y `RestExceptionHandler`.
- **Tests que cambian:** `MigrationIT` (las columnas esperadas de `users` y, sobre todo, sus tres inserts: sin `display_name` y `email` fallarían por `NOT NULL` y los tests de los `CHECK` pasarían por la razón equivocada), `SeedUsersIT`, `AuthControllerIT`, `UserTest`, `AuthServiceTest` y `JwtTokenIssuerTest` (todos construyen `User` con la firma vieja). `UserRepositoryAdapterIT`, `RoleRestrictionIT` y `OpenApiIT` se revisan. Los ITs de la spec 01 solo leen `LoginResponse.token()`, que sigue existiendo.
- **Documentación:** README (tabla de usuarios con nombre y correo, ejemplo de `curl` de `/auth/me`) y `CLAUDE.md`.

### 10.6 Estrategia de pruebas

| Requisito | Prueba |
|---|---|
| REQ-15 | `MigrationIT`: `users` tiene `display_name` y `email`, ambos `NOT NULL`, y un valor en blanco viola el `CHECK`. `UsersProfileUpgradeIT` (Flyway sin contexto de Spring, base propia): migra hasta `target("3")`, guarda `id`, `password_hash` y `technician_id` de los cinco usuarios, migra al final y verifica que no cambiaron y que el perfil quedó completo |
| REQ-16 | `SeedUsersIT`: los cinco usuarios con el nombre y el correo de la tabla de §10.2. `UsersProfileUpgradeIT`: migrando solo `db/migration`, `users` queda vacía |
| REQ-17 | `AuthControllerIT`: login de cada rol devuelve `token` y un `user` con `id` (string), `username`, `displayName`, `email` y `role` |
| REQ-18 | `AuthControllerIT`: `tecnico` trae `legajo` `1001`, `specialty` `mecanico` y `teamType` `guardia`; `electricista`, `1002`, `electricista` y `preventivo-correctivo` (los datos del maestro de la spec 01) |
| REQ-19 | `AuthControllerIT`: para `admin`, `teamleader` y `produccion` las claves de `user` son exactamente `id`, `username`, `displayName`, `email` y `role` |
| REQ-20 | `AuthControllerIT`: `/auth/me` devuelve un objeto igual al `user` del login, para un técnico y para un no-técnico |
| REQ-21 | `AuthControllerIT`: login como `tecnico`; `PUT /technicians/1001` (con `teamleader`) cambia su `teamType`; `/auth/me` con el **mismo token** devuelve el valor nuevo; el test restaura el dato |
| REQ-22 | `AuthControllerIT`: ni la respuesta del login ni la de `/me` contienen `password`, `passwordHash` ni un texto que empiece con `$2` |
| REQ-23 | `JwtTokenIssuerTest`: los claims de un técnico son exactamente `sub`, `role`, `legajo`, `iat` y `exp`. `AuthControllerIT`: el token decodificado del login no tiene `specialty` ni `teamType` |
| REQ-24 | `AuthControllerIT`: se inserta un usuario, se inicia sesión, se borra la fila y `/auth/me` con ese token responde `401` con el mismo `message` que para un token basura. `AuthServiceTest`: `currentUser` de un usuario inexistente lanza `UnknownSessionUserException` |
| Invariantes | `UserTest`: las del perfil del técnico y del no-técnico; `displayName` y `email` en blanco |

### 10.7 Puntos que decidí yo y conviene confirmar

1. **`specialty` y `teamType` como `String` en `auth.domain`**, para no cerrar un ciclo con `maintenance.domain` (§10.1). La alternativa es mover `Specialty` y `TeamType` a `shared/domain`, que toca código de la spec 01.
2. **`V4` + `V4_1` + `V5`**, y `displayName` y `email` no vacíos por `CHECK`, sin `UNIQUE` en `email`.
3. **Los `id` del seed** quedan `"1"` a `"5"` por orden de inserción (distintos de `db.json`).
4. **Rol de `/me` desde la base, autorización con el rol del token** (hoy equivalentes).
