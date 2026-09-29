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
