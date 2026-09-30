# Spec 00 — Proyecto base y seguridad

## Contexto y decisiones

Primera spec del backend `angular-enterprise-lab-api` (repo nuevo, separado
del frontend). Todo lo demás (work orders, mantenimiento) depende de esto,
así que se cierra completo antes de avanzar a dominio de negocio.

Alcance descartado explícitamente para esta spec:
- CRUD de órdenes, técnicos o equipos — specs propias, después de esta.
- Refresh tokens / rotación de tokens — se documenta como decisión técnica
  pendiente en `design.md`, no bloquea el cierre de esta spec.
- Registro de usuarios (alta de cuentas) — hoy los usuarios de prueba del
  frontend se cargan por seed (`db.json`); acá se replica con un seed
  Flyway, no con un endpoint de registro público.
- Recuperación de contraseña — fuera de alcance, no existe en el frontend
  actual tampoco.

Restricción tomada del frontend existente: los cuatro roles
(`administrador`, `team-leader-mantenimiento`, `personal-produccion`,
`tecnico`) y el hecho de que un `tecnico` tiene `legajo`, especialidad y
tipo de equipo (ver `auth.model.ts` y specs 010/013b/013c del frontend) se
mantienen tal cual — no se rediseñan acá.

## Requisitos

### REQ-1: Arranque de la aplicación
CUANDO se ejecuta la aplicación con la configuración por defecto
EL SISTEMA DEBERÁ iniciar y exponer un endpoint de health check
(`GET /actuator/health`) que responda `200 OK` con estado `UP`.

### REQ-2: Conexión a base de datos vía Docker Compose
CUANDO se ejecuta `docker compose up` con la configuración del repo
EL SISTEMA DEBERÁ levantar una instancia de PostgreSQL accesible por la
aplicación con las credenciales definidas en `application.yml`.

### REQ-3: Fallo de conexión a base de datos al iniciar
CUANDO la aplicación arranca y no puede conectarse a PostgreSQL
EL SISTEMA DEBERÁ fallar el arranque con un mensaje de error que identifique
el problema de conexión (fail fast), sin quedar en un estado parcialmente
inicializado.

### REQ-4: Migraciones versionadas con Flyway
CUANDO la aplicación arranca contra una base de datos vacía
EL SISTEMA DEBERÁ ejecutar las migraciones de Flyway en orden y dejar el
esquema (tablas `users`, `technicians`, `teams` como mínimo para esta spec:
`users`) creado según la migración `V1__init.sql`.

### REQ-5: Modelo de usuario con rol
CUANDO se crea un registro en la tabla `users` vía seed de Flyway
EL SISTEMA DEBERÁ almacenar username, contraseña hasheada, y un rol que sea
uno de los cuatro valores válidos (`administrador`,
`team-leader-mantenimiento`, `personal-produccion`, `tecnico`); si el rol es
`tecnico`, el registro DEBERÁ incluir además una referencia a `legajo`.

### REQ-6: Almacenamiento seguro de contraseñas
CUANDO se persiste una contraseña de usuario, en seed o en cualquier flujo
futuro de alta
EL SISTEMA DEBERÁ almacenarla hasheada con BCrypt (o equivalente de
`PasswordEncoder` de Spring Security), nunca en texto plano.

### REQ-7: Login exitoso
CUANDO un cliente envía `POST /auth/login` con username y contraseña que
coinciden con un usuario existente
EL SISTEMA DEBERÁ responder `200 OK` con un token JWT firmado que incluya el
username, el rol, y (si aplica) el legajo como claims.

### REQ-8: Login con credenciales inválidas
CUANDO un cliente envía `POST /auth/login` con username inexistente o
contraseña incorrecta
EL SISTEMA DEBERÁ responder `401 Unauthorized` con un cuerpo de error
consistente, sin distinguir en el mensaje si falló el username o la
contraseña (para no filtrar qué usuarios existen).

### REQ-9: Acceso a endpoint protegido sin token
CUANDO un cliente solicita un endpoint protegido sin header `Authorization`
EL SISTEMA DEBERÁ responder `401 Unauthorized`.

### REQ-10: Acceso a endpoint protegido con token inválido o expirado
CUANDO un cliente solicita un endpoint protegido con un JWT expirado,
mal firmado, o malformado
EL SISTEMA DEBERÁ responder `401 Unauthorized` sin exponer el motivo
específico de invalidez en el cuerpo de la respuesta.

### REQ-11: Autorización por rol
CUANDO un cliente autenticado con un rol sin permiso solicita un endpoint
restringido a otros roles
EL SISTEMA DEBERÁ responder `403 Forbidden`.

### REQ-12: CORS para el frontend Angular
CUANDO el frontend (`http://localhost:4200` en desarrollo) realiza una
petición a la API
EL SISTEMA DEBERÁ incluir las cabeceras CORS necesarias para que el
navegador permita la respuesta, restringido a los orígenes configurados
explícitamente (no `*`).

### REQ-13: Formato de error consistente
CUANDO ocurre cualquier error controlado (validación, autenticación,
autorización, recurso no encontrado)
EL SISTEMA DEBERÁ responder con un cuerpo JSON de forma consistente
(código, mensaje, timestamp) a través de un manejador global de
excepciones, no con el stacktrace por defecto de Spring.

### REQ-14: Documentación OpenAPI
CUANDO se accede a `/swagger-ui.html` (o la ruta que exponga springdoc)
EL SISTEMA DEBERÁ mostrar la documentación interactiva de todos los
endpoints expuestos en esta spec, generada desde las anotaciones del código
(no un documento mantenido a mano).

## Enmienda 00-A — contrato de sesión del frontend (2026-09-29)

Se agrega a esta spec en vez de abrir una nueva (regla de `CLAUDE.md`: una
enmienda edita la spec existente). Nace de comparar la spec 00 con el frontend
(`auth.model.ts`, `auth.service.ts`), que espera que el login devuelva
`{token, user}` con el perfil completo del usuario, y no solo `{token}`. **Amplía
REQ-7** (el token y sus claims no cambian) y **redefine el cuerpo de
`GET /auth/me`**. Depende de la spec 01, porque la especialidad y el tipo de
equipo del técnico viven en el maestro de técnicos que esa spec completa.

Decisiones tomadas para la enmienda:

- `user` lleva `id` (string), `username`, `displayName`, `email`, `role` y, solo
  para el rol `tecnico`, `legajo`, `specialty` y `teamType`; nunca la contraseña
  ni su hash.
- `GET /auth/me` devuelve **el mismo objeto `user`**, leído de la base en el
  momento, no de los claims del token: si el perfil del técnico cambia, la
  siguiente consulta lo refleja sin volver a iniciar sesión.
- El token **no** incluye `specialty` ni `teamType`: pueden cambiar, y la
  autoridad es la base (la spec 04 los lee de ahí para habilitar a un técnico).
- Cambio de forma en `GET /auth/me` (hoy `{username, role, legajo}`): aún no tiene
  consumidores, así que no hay que mantener compatibilidad.
- Restricción con el seed: la migración agrega columnas obligatorias a filas que
  el seed de dev ya insertó (`V1_1`), igual que en la spec 01.

### REQ-15: Nombre visible y correo del usuario
CUANDO la aplicación arranca contra una base que ya tiene las migraciones
anteriores
EL SISTEMA DEBERÁ aplicar las migraciones de Flyway que agreguen a `users`
`display_name` y `email`, ambos obligatorios, y las migraciones DEBERÁN aplicarse
también sobre los usuarios que el seed de dev ya insertó.

### REQ-16: Datos de prueba del perfil
CUANDO la aplicación arranca con el perfil `dev`
EL SISTEMA DEBERÁ dejar cargados el nombre visible y el correo de los cinco
usuarios de prueba, iguales a los de `db.json` del frontend (por ejemplo
`admin`: "Administrador", `admin@enterprise-lab.dev`; `tecnico`: "Técnico
Mecánico de Guardia", `tecnico@enterprise-lab.dev`), y no cargarlos en ningún otro
perfil.

### REQ-17: El login devuelve la sesión completa
CUANDO un cliente envía `POST /auth/login` con credenciales válidas
EL SISTEMA DEBERÁ responder `200 OK` con `{token, user}`, donde `user` contiene
`id` como string, `username`, `displayName`, `email` y `role`.

### REQ-18: Perfil del técnico en la sesión
CUANDO el usuario que inicia sesión tiene rol `tecnico`
EL SISTEMA DEBERÁ incluir en `user` su `legajo`, su `specialty` y su `teamType`,
tomados del maestro de técnicos.

### REQ-19: Sin perfil de técnico para el resto de los roles
CUANDO el usuario que inicia sesión tiene rol `administrador`,
`team-leader-mantenimiento` o `personal-produccion`
EL SISTEMA DEBERÁ omitir `legajo`, `specialty` y `teamType` de `user`, en vez de
enviarlos vacíos.

### REQ-20: `GET /auth/me` devuelve el mismo usuario
CUANDO un cliente autenticado solicita `GET /auth/me`
EL SISTEMA DEBERÁ responder `200 OK` con un objeto con la misma forma que el
`user` del login, con los datos leídos de la base en ese momento.

### REQ-21: El perfil se refresca sin volver a iniciar sesión
CUANDO el `teamType` o la `specialty` de un técnico cambia en el maestro
DESPUÉS de que inició sesión
EL SISTEMA DEBERÁ devolver el valor nuevo en la siguiente consulta de
`GET /auth/me`, con el mismo token.

### REQ-22: Nada sensible en las respuestas
CUANDO se responde `POST /auth/login` o `GET /auth/me`
EL SISTEMA DEBERÁ no incluir la contraseña ni su hash.

### REQ-23: El token no lleva datos que pueden cambiar
CUANDO se emite un token para un técnico
EL SISTEMA DEBERÁ no incluir en sus claims `specialty` ni `teamType`, y conservar
los claims de REQ-7 (`sub`, `role` y `legajo`).

### REQ-24: `GET /auth/me` con un usuario que ya no existe
CUANDO un cliente solicita `GET /auth/me` con un token válido y firmado cuyo
usuario (`sub`) ya no existe en la base
EL SISTEMA DEBERÁ responder `401 Unauthorized` con el mismo `ApiError` y el mismo
`message` que para un token inválido (REQ-10), sin revelar que el usuario existió.

### Auto-revisión de la enmienda

- Un comportamiento por requisito; los caminos no felices (credenciales inválidas,
  sin token, token inválido) ya están cubiertos por REQ-8 a REQ-10 y no cambian.
- REQ-18 y REQ-19 se separan porque son dos formas distintas de la misma
  respuesta, una por cada tipo de usuario.
- Punto que decidí yo y conviene que confirmes: **`GET /auth/me` con la forma
  completa** (REQ-20); el frontend hoy no lo llama porque restaura la sesión de
  `localStorage`, pero es el endpoint natural para rehidratarla.
- Ajustes al revisar los requisitos (2026-09-30): REQ-15 pasa de "una migración"
  a "las migraciones" (con filas ya sembradas hacen falta tres pasos, igual que en
  la spec 01) y se agrega REQ-24, porque leer la base en `GET /auth/me` (REQ-20)
  abre el caso de un token válido de un usuario que ya no existe.
- Efecto en lo ya implementado: `LoginResponse` (hoy `{token}`), `MeResponse`,
  `AuthService`, `UserEntity` y los tests `AuthControllerIT`, `SeedUsersIT` y
  `MigrationIT` cambian; hay que actualizar `design.md` y sumar tareas a
  `tasks.md` de esta spec (sin borrar las ya hechas).

## Auto-revisión

- Cada criterio cubre un solo comportamiento — ninguno mezcla dos "y"
  distintos (se separaron login-éxito / login-fallo / sin-token /
  token-inválido / sin-permiso en cinco requisitos en vez de uno).
- Caminos no felices cubiertos: fallo de conexión a DB (REQ-3), credenciales
  inválidas (REQ-8), sin token (REQ-9), token inválido/expirado (REQ-10),
  rol sin permiso (REQ-11).
- Sin adjetivos no verificables — cada requisito tiene un código HTTP o una
  condición concreta como criterio, no "debe ser seguro" o "debe responder
  rápido".
- Lo que falta a propósito y está anotado como fuera de alcance: refresh
  tokens, registro público, recuperación de contraseña — no se colaron
  como requisitos sueltos.
- Punto abierto real: REQ-5 asume que el seed de técnicos (tabla
  `technicians`) todavía no existe en esta spec, pero un usuario con rol
  `tecnico` necesita legajo. Lo resuelvo en `design.md` con una tabla
  `technicians` mínima (solo legajo, sin el resto del maestro) en esta
  misma spec, o separando el seed de `tecnico` para la spec de
  mantenimiento. Lo marco para decidir en diseño, no lo resuelvo acá.
