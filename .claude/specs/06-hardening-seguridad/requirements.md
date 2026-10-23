# Spec 06 — Hardening de seguridad

## Contexto y decisiones

Spec 06 del backend (transversal: `auth`, `shared` y `workorders`). Sale del
cambio **C3** de `ROADMAP-post-auditoria.md` (raíz del repo), que a su vez sale de la
auditoría del backend del 2026-10-22 (`.claude/audits/2026-10-22-auditoria-backend.md`):
puntos 4 (sin rate limiting), 6 (`DB_PASSWORD` con default), 7 (Swagger público en
todos los perfiles), 19 (excepciones de Spring MVC que responden `500`), 18 de
riesgos (reintentos agotados → `500`) y la recomendación de validar `JWT_SECRET`.

Numeración: el `ROADMAP.md` de specs reserva la 06 para "Operación y producción
(opcional)", cuyo alcance de endurecimiento (springdoc apagado, `JWT_SECRET`
obligatorio, CORS de producción) coincide con esta spec. Se usa la **06** para el
endurecimiento; lo demás de "operación" (Dockerfile, Dependabot, acciones por SHA,
versión de OpenAPI) queda en **C9 / spec 11**. Refresh tokens pasa al backlog del
roadmap post-auditoría. Ver pregunta abierta 3.

Decisiones tomadas (del roadmap post-auditoría, sección 0 y C3):

- El rate limiting de `POST /auth/login` es **por IP y por usuario**, en memoria
  (hay una sola instancia); la limitación se documenta.
- Respuesta de bloqueo: `429` con el `ApiError` de siempre (`code: TOO_MANY_ATTEMPTS`)
  y cabecera `Retry-After`.
- Swagger y `/v3/api-docs` solo existen en el perfil `dev`; el `permitAll`
  correspondiente queda condicionado al perfil.
- `DB_PASSWORD` y `JWT_SECRET` se validan al arrancar; la aplicación no arranca si no
  cumplen.
- Contrato de errores sin cambios: `{code, message, timestamp, path, details?}`.
- **Ventana**: fija, empieza en el primer fallo de la clave (usuario o IP) y dura 15
  minutos; los fallos se cuentan dentro de ella. Al alcanzar el límite, la clave queda
  bloqueada 15 minutos contados desde ese fallo. Al vencer la ventana o el bloqueo, el
  contador vuelve a cero.
- Cuenta como fallo solo un `POST /auth/login` con cuerpo válido y credenciales
  incorrectas (`401 INVALID_CREDENTIALS`). Un cuerpo mal formado o sin `username` es
  `400` y no cuenta.
- Sin migraciones de Flyway: esta spec no toca el esquema.
- Despliegue: **API primero**. Cuando se agrega el `429`, el frontend lo muestra en su
  enmienda 19-A (otro repo, otra sesión).

Decisiones aprobadas el 2026-10-22 (se aprobó la spec con los defaults propuestos):

- Umbrales: 5 intentos fallidos por usuario y 20 por IP, ventana y bloqueo de 15
  minutos, todo configurable.
- IP: se toma la dirección de la conexión y no se confía en `X-Forwarded-For`. Si el
  proxy del VPS lo exige, se agrega una propiedad de proxies confiables en una enmienda
  06-A al cerrar F0. **Hasta entonces, detrás de un proxy el contador de IP es único para
  todos los usuarios:** no desplegar esta spec sin resolver F0.
- Bloqueo por usuario: se acepta que quien conozca un `username` pueda bloquear la
  cuenta 15 minutos.
- No se manejan otras excepciones de Spring MVC (por ejemplo `406`): siguen en `500`
  genérico.
- `405` y `415` se producen antes de la autorización de dominio: un rol sin permiso que
  envíe un `Content-Type` inválido recibe `415`, no `403`. La autenticación va primero.
- CORS fuera de `dev`: se mantiene el default `http://localhost:4200`; solo se documenta.
- Numeración: spec 06 para C3; C9 queda como spec 11.
- Requisito de despliegue: como `JWT_SECRET` y `DB_PASSWORD` pasan a validarse al
  arrancar, deben estar definidas en el servicio de producción **antes** de desplegar.

## Fuera de alcance

- Refresh token, logout con revocación y claims `iss`/`aud`/`jti` (backlog).
- Rate limiting distribuido o persistente (Redis, base de datos): en memoria alcanza
  para una instancia; se reinicia con la aplicación.
- Rate limiting de endpoints que no sean `POST /auth/login` (`/auth/guest` lo hereda en
  la spec 08).
- CAPTCHA, 2FA, notificación al usuario bloqueado, desbloqueo manual por un
  administrador.
- Dockerfile, `docker-compose.yaml`, CI, Dependabot, versión de OpenAPI (spec 11).
- Usuario y URL de la base de datos (siguen fijos en `application.yml`).
- Alta de usuarios y bootstrap del administrador (spec 07).
- Cambios en el frontend (enmienda 19-A).

## REQ-1: Bloqueo de login por usuario

**Historia de usuario:** Como responsable del sistema, quiero que un usuario deje de
poder intentar contraseñas tras varios fallos seguidos, para frenar ataques de
diccionario contra una cuenta.

### Criterios de aceptación
1. CUANDO el 5.º intento fallido contra el mismo `username` ocurre dentro de la
   ventana de 15 minutos
   EL SISTEMA DEBERÁ responder `401 INVALID_CREDENTIALS` a ese 5.º intento.
   *Falla si:* el 5.º intento responde `429` (el bloqueo empieza antes de tiempo).
   1b. CUANDO llega el 6.º intento con ese `username` dentro del bloqueo
   EL SISTEMA DEBERÁ responder `429` al `POST /auth/login`, aunque la contraseña sea
   correcta.
   *Falla si:* el 6.º intento con la contraseña correcta responde `200` o `401`.
2. CUANDO el `username` no existe en la base
   EL SISTEMA DEBERÁ contar sus fallos y bloquearlo igual que a uno existente, con la
   misma respuesta.
   *Falla si:* un `username` inexistente nunca recibe `429` (permitiría distinguir cuentas
   existentes).
3. CUANDO un login exitoso ocurre con menos de 5 fallos acumulados para ese `username`
   EL SISTEMA DEBERÁ reiniciar a cero su contador.
   *Falla si:* 4 fallos, 1 éxito y 4 fallos más producen `429`.
4. CUANDO se compara el `username` para contar y bloquear
   EL SISTEMA DEBERÁ ignorar mayúsculas y espacios de los bordes (`Admin` y ` admin `
   comparten contador).
   *Falla si:* alternar mayúsculas permite superar los 5 intentos.
   4b. CUANDO el cuerpo del login es mal formado o el `username` falta o es vacío
   EL SISTEMA DEBERÁ responder `400 VALIDATION_ERROR` y no sumar ningún fallo.
   *Falla si:* 10 cuerpos vacíos seguidos bloquean a una IP o a un usuario.
5. CUANDO transcurren 15 minutos desde el fallo que llevó al bloqueo
   EL SISTEMA DEBERÁ aceptar de nuevo intentos de ese `username`.
   *Falla si:* con la contraseña correcta, pasados los 15 minutos, sigue respondiendo
   `429`, o si lo levanta antes de tiempo.

## REQ-2: Bloqueo de login por IP

**Historia de usuario:** Como responsable del sistema, quiero limitar los intentos
fallidos desde una misma IP, para frenar la prueba de muchos usuarios desde un solo
origen.

### Criterios de aceptación
1. CUANDO el 20.º intento fallido (de cualquier `username`) ocurre desde la misma IP
   dentro de la ventana de 15 minutos
   EL SISTEMA DEBERÁ responder `429` al siguiente `POST /auth/login` desde esa IP,
   aunque el `username` y la contraseña sean correctos y el `username` sea otro.
   *Falla si:* el intento 21.º con credenciales válidas responde `200`.
2. CUANDO una IP está bloqueada
   EL SISTEMA DEBERÁ seguir aceptando intentos desde otra IP.
   *Falla si:* el bloqueo de una IP afecta a las demás.
3. SI la aplicación no está configurada para confiar en un proxy
   ENTONCES EL SISTEMA DEBERÁ tomar la IP de la dirección remota de la conexión e
   ignorar `X-Forwarded-For` y `Forwarded`.
   *Falla si:* cambiar esas cabeceras en cada request evita el bloqueo.
4. CUANDO transcurren 15 minutos desde el fallo que llevó al bloqueo de la IP
   EL SISTEMA DEBERÁ aceptar de nuevo intentos desde esa IP.
   *Falla si:* sigue bloqueada pasado el tiempo, o se levanta antes.

## REQ-3: Respuesta de bloqueo

**Historia de usuario:** Como cliente de la API, quiero saber que estoy bloqueado y
cuánto esperar, para mostrarle un mensaje útil al usuario.

### Criterios de aceptación
1. CUANDO un `POST /auth/login` es rechazado por rate limiting
   EL SISTEMA DEBERÁ responder `429` con `{code: "TOO_MANY_ATTEMPTS", message, timestamp,
   path: "/auth/login"}`.
   *Falla si:* el cuerpo es vacío o HTML, o el `code` es otro.
   1b. CUANDO el bloqueo es por usuario y cuando es por IP
   EL SISTEMA DEBERÁ devolver exactamente el mismo `message` ("Demasiados intentos
   fallidos. Probá de nuevo más tarde").
   *Falla si:* los dos `message` difieren, o el texto indica cuál límite se alcanzó o si el
   `username` existe.
2. CUANDO se responde `429`
   EL SISTEMA DEBERÁ incluir `Retry-After` con un entero de segundos, mayor o igual que
   1, igual al tiempo que falta para levantar el bloqueo (el mayor de los dos si están
   bloqueados usuario e IP).
   *Falla si:* falta la cabecera, no es un entero, es `0` o negativo, o difiere del tiempo
   restante en más de 1 segundo.
3. CUANDO el origen de la petición está en `app.cors.allowed-origins`
   EL SISTEMA DEBERÁ exponer `Retry-After` en `Access-Control-Expose-Headers`, para que
   el navegador del frontend pueda leerla.
   *Falla si:* una respuesta `429` a un origen permitido no lista `Retry-After` en esa
   cabecera.
4. CUANDO un intento está bloqueado
   EL SISTEMA DEBERÁ responder `429` sin verificar la contraseña (sin llamar a
   `PasswordEncoder.matches`) y sin consultar al repositorio de usuarios.
   *Falla si:* un intento bloqueado ejecuta BCrypt o consulta la base.
5. CUANDO un intento bloqueado llega
   EL SISTEMA DEBERÁ no sumarlo al contador de fallos ni extender el bloqueo.
   *Falla si:* insistir mientras se está bloqueado alarga el tiempo de espera.
6. CUANDO se llama a cualquier endpoint distinto de `POST /auth/login`
   EL SISTEMA DEBERÁ no aplicarle este límite.
   *Falla si:* una IP o usuario bloqueados en el login reciben `429` en `GET /auth/me` o
   en cualquier otro endpoint.

## REQ-4: Configuración y memoria del límite

**Historia de usuario:** Como responsable del despliegue, quiero ajustar los límites sin
recompilar y que no consuman memoria sin tope, para operar la instancia única.

### Criterios de aceptación
1. CUANDO se definen `app.security.login-rate-limit.max-attempts-per-user`,
   `…max-attempts-per-ip` y `…window`
   EL SISTEMA DEBERÁ aplicar esos valores como umbrales y duración.
   *Falla si:* cambiar una propiedad no cambia el umbral observado en un test.
   1b. SI las propiedades no se definen
   ENTONCES EL SISTEMA DEBERÁ usar 5 intentos por usuario, 20 por IP y 15 minutos.
   *Falla si:* un test sin propiedades observa otros valores.
2. SI algún valor es cero, negativo o no es una duración válida
   ENTONCES EL SISTEMA DEBERÁ fallar al arrancar con un mensaje que nombra la propiedad.
   *Falla si:* arranca con `max-attempts-per-user: 0`.
3. CUANDO vence la ventana de una clave (usuario o IP)
   EL SISTEMA DEBERÁ descartar su registro de la memoria.
   *Falla si:* tras vencer todas las ventanas el almacén conserva entradas.
4. CUANDO llegan intentos de login concurrentes de un mismo usuario o IP
   EL SISTEMA DEBERÁ dejar pasar a la verificación de contraseña como máximo el umbral
   configurado y responder `429` a los demás.
   *Falla si:* con umbral 5 y 50 intentos simultáneos, más de 5 llegan a la verificación
   de contraseña. **Enmienda 2026-10-22:** el texto original pedía contar los 50 fallos;
   contradice REQ-3.5 (los bloqueados no suman) y no cubre la carrera real, en que todos
   pasan el control antes de que el primero falle.

## REQ-5: Swagger y OpenAPI solo en `dev`

**Historia de usuario:** Como responsable de seguridad, quiero que el contrato de la API
no se publique en producción, para no facilitar el reconocimiento.

### Criterios de aceptación
1. CUANDO el perfil `dev` no está activo
   EL SISTEMA DEBERÁ responder `401` a `GET /swagger-ui.html`, `GET /swagger-ui/index.html`
   y `GET /v3/api-docs` cuando no se envía token.
   *Falla si:* alguno de esos tres recursos responde `200` o `404` sin token.
   1b. CUANDO el perfil `dev` no está activo y se envía un token válido
   EL SISTEMA DEBERÁ responder `404` con el `ApiError` de siempre a esos tres recursos.
   *Falla si:* alguno responde `200`.
2. CUANDO el perfil `dev` está activo
   EL SISTEMA DEBERÁ responder `200` sin token a `GET /swagger-ui.html` (tras la
   redirección de springdoc) y a `GET /v3/api-docs`.
   *Falla si:* alguno no responde `200` en `dev`.
3. CUANDO el perfil `dev` no está activo
   EL SISTEMA DEBERÁ no tener los endpoints de documentación en el contexto de Spring.
   *Falla si:* existe un bean de `OpenApiWebMvcResource` sin el perfil `dev`.

## REQ-6: `DB_PASSWORD` obligatoria fuera de `dev`

**Historia de usuario:** Como responsable del despliegue, quiero que la aplicación no
arranque con una clave de base de datos por defecto, para no llegar a producción con una
credencial conocida.

### Criterios de aceptación
1. SI el perfil `dev` no está activo y `DB_PASSWORD` no está definida (o está vacía)
   ENTONCES EL SISTEMA DEBERÁ fallar al arrancar con un mensaje que nombra `DB_PASSWORD`.
   *Falla si:* arranca usando `enterpriselab`.
2. CUANDO el perfil `dev` está activo y `DB_PASSWORD` no está definida
   EL SISTEMA DEBERÁ arrancar usando la clave por defecto de desarrollo.
   *Falla si:* `./gradlew bootRun --args='--spring.profiles.active=dev'` deja de arrancar
   sin la variable.
3. CUANDO `DB_PASSWORD` está definida fuera de `dev`
   EL SISTEMA DEBERÁ usarla para conectarse.
   *Falla si:* ignora el valor definido.

## REQ-7: `JWT_SECRET` de al menos 32 bytes

**Historia de usuario:** Como responsable del despliegue, quiero que un secreto débil
impida el arranque, para no firmar tokens con una clave corta.

### Criterios de aceptación
1. SI `JWT_SECRET` tiene menos de 32 bytes en UTF-8
   ENTONCES EL SISTEMA DEBERÁ fallar al arrancar con un mensaje que nombra `JWT_SECRET`,
   el mínimo (32 bytes) y la longitud recibida en bytes.
   *Falla si:* un secreto de 16 bytes arranca.
2. SI `JWT_SECRET` tiene exactamente 32 bytes
   ENTONCES EL SISTEMA DEBERÁ arrancar.
   *Falla si:* 32 bytes es rechazado (error de borde).
3. CUANDO falla la validación
   EL SISTEMA DEBERÁ no incluir el valor del secreto en el mensaje ni en los logs.
   *Falla si:* el texto del secreto aparece en la salida del arranque fallido.
4. CUANDO el perfil `dev` usa su secreto por defecto
   EL SISTEMA DEBERÁ seguir arrancando.
   *Falla si:* el default de `dev` queda por debajo del mínimo.
5. SI `JWT_SECRET` no está definida o está vacía fuera de `dev`
   ENTONCES EL SISTEMA DEBERÁ fallar al arrancar con un mensaje que dice que `JWT_SECRET`
   no está definida.
   *Falla si:* arranca sin secreto. **Enmienda 2026-10-22:** el texto original decía
   "seguir fallando (comportamiento actual)", pero el comportamiento actual no existe:
   Spring deja el placeholder sin resolver como texto literal (`${JWT_SECRET}`, 13 bytes)
   al enlazar `@ConfigurationProperties`, y la aplicación arranca firmando tokens con esa
   clave pública. Los tests sin perfil `dev` lo prueban: arrancan sin la variable.

## REQ-8: Método HTTP no soportado

**Historia de usuario:** Como cliente de la API, quiero un `405` claro cuando uso un
método que el recurso no admite, para distinguirlo de un fallo del servidor.

### Criterios de aceptación
1. CUANDO un usuario autenticado envía `DELETE /work-orders` (sin id)
   EL SISTEMA DEBERÁ responder `405` con `{code: "METHOD_NOT_ALLOWED", message, timestamp,
   path}` y la cabecera `Allow` con los métodos admitidos.
   *Falla si:* responde `500`, o `405` sin cuerpo `ApiError`.

## REQ-9: Tipo de contenido no soportado

**Historia de usuario:** Como cliente de la API, quiero un `415` cuando envío un
`Content-Type` que el endpoint no acepta.

### Criterios de aceptación
1. CUANDO un usuario autenticado envía `POST /work-orders` con `Content-Type: text/plain`
   EL SISTEMA DEBERÁ responder `415` con `{code: "UNSUPPORTED_MEDIA_TYPE", message,
   timestamp, path}`.
   *Falla si:* responde `500`.
2. CUANDO se envía `POST /auth/login` con `Content-Type: text/plain`
   EL SISTEMA DEBERÁ responder `415` con el mismo formato.
   *Falla si:* responde `500` o `401`.

## REQ-10: Parámetros faltantes o mal tipados

**Historia de usuario:** Como cliente de la API, quiero un `400` descriptivo cuando un
parámetro falta o tiene un tipo inválido.

### Criterios de aceptación
1. CUANDO una petición autenticada omite un parámetro de consulta obligatorio
   EL SISTEMA DEBERÁ responder `400` con `{code: "VALIDATION_ERROR", …}` y
   `details: {<parámetro>: <mensaje>}`.
   *Falla si:* responde `500`, o `400` sin `details` con el nombre del parámetro.
2. CUANDO una petición autenticada envía un parámetro con un tipo que no se puede
   convertir
   EL SISTEMA DEBERÁ responder `400` con el mismo formato.
   *Falla si:* responde `500`.
3. CUANDO un usuario autenticado envía `GET /work-orders?page=abc`
   EL SISTEMA DEBERÁ seguir respondiendo `400` con `details.page` (comportamiento actual
   de la spec 03).
   *Falla si:* deja de responder `400` o `details` no incluye `page`.

## REQ-11: Recurso inexistente

**Historia de usuario:** Como cliente de la API, quiero un `404` en el formato estándar
cuando pido una ruta que no existe.

### Criterios de aceptación
1. CUANDO un usuario autenticado pide una ruta sin controlador (por ejemplo
   `GET /no-existe`)
   EL SISTEMA DEBERÁ responder `404` con `{code: "NOT_FOUND", message, timestamp, path}`.
   *Falla si:* responde `500` o HTML.
2. CUANDO un usuario sin token pide una ruta inexistente
   EL SISTEMA DEBERÁ responder `401` (la autenticación se evalúa antes).
   *Falla si:* responde `404` sin token.

## REQ-12: Los errores inesperados siguen siendo `500` genérico

**Historia de usuario:** Como responsable de seguridad, quiero que ampliar el manejo de
errores no deje pasar detalles internos ni convierta fallos reales en errores de cliente.

### Criterios de aceptación
1. CUANDO ocurre una excepción no prevista dentro de un controlador
   EL SISTEMA DEBERÁ responder `500` con `{code: "INTERNAL_ERROR", message: "Ocurrió un
   error inesperado", …}` y registrar la traza solo en el log.
   *Falla si:* el cuerpo contiene el nombre de la excepción, un mensaje interno o una
   traza, o si deja de responder `500`.

## REQ-13: Reintentos agotados al cambiar el estado de una orden

**Historia de usuario:** Como técnico, quiero un `409` claro cuando mi acción sobre una
orden choca varias veces con otras, para volver a intentarlo en vez de ver un error
del servidor.

### Criterios de aceptación
1. CUANDO una transición de una orden (`take`, `close`, `release` o `cancel`) toca 0
   filas en los 3 intentos
   EL SISTEMA DEBERÁ responder `409` con `{code: "WORK_ORDER_CONCURRENT_UPDATE", message,
   timestamp, path}`.
   *Falla si:* responde `500` o se lanza `IllegalStateException`.
2. CUANDO se agotan los reintentos
   EL SISTEMA DEBERÁ no modificar la orden.
   *Falla si:* la orden cambia de estado, dueño o nota.
3. CUANDO se agotan los reintentos
   EL SISTEMA DEBERÁ registrar un `warn` con el id de la orden.
   *Falla si:* no hay registro del evento en el log.

## REQ-14: Documentación de configuración de producción y del límite

**Historia de usuario:** Como responsable del despliegue, quiero saber qué variables
definir y qué límites tiene el rate limiting, para configurar el servicio sin leer el
código.

### Criterios de aceptación
1. CUANDO se lee el `README.md`
   EL SISTEMA DEBERÁ tener una sección de variables de producción con `JWT_SECRET`
   (obligatoria, mínimo 32 bytes).
   *Falla si:* falta la variable o su regla.
   1b. CUANDO se lee esa sección
   EL SISTEMA DEBERÁ incluir `DB_PASSWORD` (obligatoria fuera de `dev`).
   *Falla si:* falta o figura como opcional.
   1c. CUANDO se lee esa sección
   EL SISTEMA DEBERÁ incluir `app.cors.allowed-origins` (variable
   `APP_CORS_ALLOWED_ORIGINS`, lista separada por comas, sin comodín).
   *Falla si:* falta o no dice que el comodín es un error.
2. CUANDO se lee el `README.md`
   EL SISTEMA DEBERÁ documentar que el rate limiting es en memoria, de una sola
   instancia, que se reinicia con la aplicación, y las tres propiedades con sus valores
   por defecto.
   *Falla si:* no menciona la limitación o los valores.
3. CUANDO se lee la documentación OpenAPI en `dev`
   EL SISTEMA DEBERÁ mostrar la respuesta `429` (`ApiError`, cabecera `Retry-After`) en
   `POST /auth/login`.
   *Falla si:* `/v3/api-docs` no incluye el código `429` en ese endpoint.
4. CUANDO se lee la tabla de endpoints del `README.md`
   EL SISTEMA DEBERÁ indicar que Swagger y `/v3/api-docs` solo están disponibles con el
   perfil `dev`.
   *Falla si:* sigue figurando como "público".

## Requisitos no funcionales

- NFR-1: Los secretos (`JWT_SECRET`, `DB_PASSWORD`, contraseñas de login) no se
  escriben en ningún log ni mensaje de error. *Falla si:* aparecen en la salida de un
  arranque fallido o de un login bloqueado.
- NFR-2: Esta spec no agrega migraciones ni dependencias externas nuevas; el límite se
  implementa con la biblioteca estándar y Spring. *Falla si:* `db/migration` cambia o
  `build.gradle.kts` suma una librería de rate limiting.
- NFR-3: Todos los tests existentes siguen en verde, salvo los que hoy dan por
  supuesto que Swagger es público fuera de `dev` o que `DB_PASSWORD` tiene default,
  que se ajustan explícitamente y se listan en el diseño. *Falla si:* `./gradlew test`
  reporta fallos.

## Preguntas abiertas

Si no respondés una, queda vigente el default indicado y se registra en "Decisiones".

1. **Valores del límite** (propuestos por mí): 5 fallos por usuario, 20 por IP, ventana y
   bloqueo de 15 minutos. ¿Los confirmás o preferís otros?
2. **IP detrás del proxy del VPS** (depende de F0, punto 2): si la API está detrás de un
   proxy, la dirección remota es la del proxy y **todos los usuarios compartirían el
   mismo contador de IP**, con lo que 20 fallos de cualquiera bloquearían el login de
   todos. Esta spec toma la IP de la conexión y no confía en `X-Forwarded-For` por
   defecto (REQ-2.3). ¿Querés que agregue una propiedad (por ejemplo
   `server.forward-headers-strategy` o `app.security.trusted-proxies`) para confiar en
   el proxy de `/opt/web-stack`, o lo resolvemos al cerrar F0?
3. **Numeración**: el roadmap dice que si el `ROADMAP.md` ya reserva la 06 para
   "operación y producción", se absorba C9 ahí y se renumere el resto. Yo tomé la 06 solo
   para C3 (por coincidir en alcance) y dejé C9 como spec 11, sin renumerar. ¿Está bien?
4. **Bloqueo por usuario como vector de denegación**: cualquiera que conozca el
   `username` (`admin`) puede bloquear su cuenta 15 minutos enviando 5 fallos. Lo acepto
   como costo del requisito del roadmap; ¿lo aceptás o querés una alternativa (por
   ejemplo, contar por par IP + usuario)?
5. **Otras excepciones estándar de Spring MVC** (por ejemplo `406` por `Accept` no
   soportado): la spec cubre solo las que pide el roadmap (405, 415, 400, 404) y deja las
   demás como `500` genérico (REQ-12). ¿Las sumamos?
6. **Orden de errores**: `405` y `415` se producen antes de entrar al controlador, así que
   un rol sin permiso que envíe un `Content-Type` inválido recibe `415`, no `403`
   (la autenticación sigue yendo primero). ¿Está bien?
7. **CORS fuera de `dev`**: el default `http://localhost:4200` del `application.yml`
   base se mantiene; no se vuelve obligatorio definir el origen en producción (el
   roadmap solo pide documentarlo). ¿Querés que falle el arranque si falta?

## Verificación final
<!-- Completar al cerrar la spec, después de terminar tasks.md. -->
| Criterio | ¿Cumplido? | Evidencia |
|---|---|---|
| REQ-1.1, 1.1b, 1.2, 1.3, 1.4, 1.5 | SÍ | LoginAttemptLimiterTest#req1_*, LoginRateLimitIT#req1_* |
| REQ-1.4b | SÍ | LoginRateLimitIT#req1_4b_ |
| REQ-2.1, 2.2, 2.4 | SÍ | LoginAttemptLimiterTest#req2_*, LoginRateLimitIT#req2_1_/req2_2_ (2.4 solo a nivel de dominio) |
| REQ-2.3 | SÍ | LoginRateLimitRealHttpIT#req2_3_ (HTTP real) + server.forward-headers-strategy: none |
| REQ-3.1, 3.1b, 3.2 | SÍ | RestExceptionHandlerTest#req3_*, LoginRateLimitIT#req3_* |
| REQ-3.3 | SÍ | CorsIT#req3_3_ |
| REQ-3.4 | SÍ | AuthServiceTest#req3_4_ |
| REQ-3.5, 3.6 | SÍ | LoginAttemptLimiterTest#req3_5_, LoginRateLimitIT#req3_5_/req3_6_ |
| REQ-4.1, 4.1b, 4.2 | SÍ | LoginRateLimitPropertiesTest#req4_* |
| REQ-4.3, 4.4 | SÍ | LoginAttemptLimiterTest#req4_3_/req4_4_ (50 hilos, pasan exactamente 5) |
| REQ-5.1, 5.1b, 5.3 | SÍ | OpenApiDisabledIT#req5_* |
| REQ-5.2 | SÍ | OpenApiIT (dev): /v3/api-docs y /swagger-ui.html responden 200 sin token (tests sin el id en el nombre) |
| REQ-6.1, 6.3, 7.1, 7.2, 7.3, 7.5 | SÍ | StartupConfigCheckerTest#req6_1_/req7_*, StartupConfigIT#req6_*/req7_* |
| REQ-6.2, 7.4 | SÍ | StartupConfigIT#req6_2_and_req7_4_ (el bootRun literal no se ejecutó) |
| REQ-8.1, 9.1, 9.2, 10.3, 11.1, 11.2 | SÍ | WebErrorsIT#req8_1_/req9_*/req10_3_/req11_* |
| REQ-10.1, 10.2, 12.1 | SÍ | RestExceptionHandlerTest#req10_*/req12_1_ |
| REQ-13.1, 13.2, 13.3 | SÍ | WorkOrderFlowServiceTest#req13_* (take, close, release, cancel) |
| REQ-14.1, 14.1b, 14.1c, 14.2, 14.4 | SÍ | README.md:29-66 y :308; greps en notes.md |
| REQ-14.3 | SÍ | OpenApiIT#req14_3_ |
| NFR-1 | SÍ | LoginRateLimitIT#nfr1_, StartupConfigCheckerTest#nfr1_, StartupConfigIT#nfr1_ |
| NFR-2 | SÍ | git status: sin cambios en db/migration ni build.gradle.kts |
| NFR-3 | SÍ | ./gradlew build: 1206 tests, 0 fallos, 0 errores, 0 omitidos |

Verificado el 2026-10-22 por `spec-verifier`: 47 criterios y 3 NFR en SÍ, ninguno en NO. Calidad de evidencia: sin tests omitidos ni aserciones vacías; no existe `tests-baseline.txt` (dos tests de `WorkOrderFlowServiceTest` cambiaron a propósito por REQ-13). Pendiente de confirmación manual (sugerida por el verificador): `./gradlew bootRun --args='--spring.profiles.active=dev'` sin variables (REQ-6.2).

---
**Estado:** Cerrada el 2026-10-22 (aprobada el mismo día; enmiendas a REQ-4.4 y REQ-7.5 por un hallazgo del diseño)
