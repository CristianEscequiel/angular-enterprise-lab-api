# Notas — Spec 06 Hardening de seguridad

## 2026-10-22 — Tarea 9: verificación manual de la documentación (REQ-14)

Salida de `grep` sobre `README.md` (recortada a 150 caracteres por línea):

**REQ-14.1**
```
$ grep -n JWT_SECRET  README.md
29:   Fuera del perfil `dev` hay que definir `JWT_SECRET` (mínimo 32 bytes) y
46:| `JWT_SECRET` | Obligatoria. Mínimo 32 bytes (UTF-8). Firma los tokens HS256. |
```

**REQ-14.1b**
```
$ grep -n DB_PASSWORD  README.md
30:   `DB_PASSWORD`; sin ellas la aplicación no arranca (ver
47:| `DB_PASSWORD` | Obligatoria fuera de `dev` (sin valor por defecto). En `dev` vale `enterpriselab` si no se define. |
```

**REQ-14.1c**
```
$ grep -n APP_CORS_ALLOWED_ORIGINS  README.md
48:| `APP_CORS_ALLOWED_ORIGINS` (propiedad `app.cors.allowed-origins`) | Orígenes del frontend, separados por comas. Un comodín (`*`) es un error de
```

**REQ-14.1c (comodín)**
```
$ grep -n -i comodín  README.md
48:| `APP_CORS_ALLOWED_ORIGINS` (propiedad `app.cors.allowed-origins`) | Orígenes del frontend, separados por comas. Un comodín (`*`) es un error de
```

**REQ-14.2 (propiedad usuario)**
```
$ grep -n login-rate-limit.max-attempts-per-user  README.md
62:| `app.security.login-rate-limit.max-attempts-per-user` | `5` |
```

**REQ-14.2 (propiedad IP)**
```
$ grep -n login-rate-limit.max-attempts-per-ip  README.md
63:| `app.security.login-rate-limit.max-attempts-per-ip` | `20` |
```

**REQ-14.2 (valor 15m)**
```
$ grep -n 15m  README.md
64:| `app.security.login-rate-limit.window` | `15m` (ventana y duración del bloqueo) |
```

**REQ-14.2 (en memoria)**
```
$ grep -n -i en memoria  README.md
66:Limitaciones: el contador está **en memoria**, de una sola instancia, y se reinicia con la
```

**REQ-14.4 (Swagger solo con dev)**
```
$ grep -n -i swagger.*perfil .dev  README.md
36:   - Swagger UI: <http://localhost:8080/swagger-ui.html> (solo con el perfil `dev`)
50:Swagger y `/v3/api-docs` solo existen con el perfil `dev`. Si se fija
308:| `/swagger-ui.html`, `/v3/api-docs/**` | público, solo con el perfil `dev` | Documentación OpenAPI (fuera de `dev` responde `401` sin token y `
```

**REQ-14.4 (toda línea con "público")**: ninguna presenta Swagger como público sin aclarar `dev`.
```
283:| `GET /actuator/health` | público   | Estado de la aplicación                       |
284:| `POST /auth/login`   | público     | Devuelve `{token, user}` (JWT HS256 y el usuario completo) |
308:| `/swagger-ui.html`, `/v3/api-docs/**` | público, solo con el perfil `dev` | Documentación OpenAPI (fuera de `dev` responde `401` sin token y `
```

## 2026-10-22 — Tarea 10: cierre

- `./gradlew build`: **1206 tests, 0 fallos, 0 errores, 0 omitidos** (eran 1123; +83).
- NFR-2: `git status` no muestra cambios en `db/migration` ni en `build.gradle.kts`.
- Desvío respecto del diseño: ninguno de comportamiento. Dos tests existentes de
  `WorkOrderFlowServiceTest` esperaban `IllegalStateException` tras 3 intentos; ahora esperan
  `ConflictException WORK_ORDER_CONCURRENT_UPDATE` (REQ-13), cambio pedido por la spec.
- Enmiendas a requisitos hechas durante el diseño: REQ-4.4 y REQ-7.5 (ver `requirements.md`).

### Orden de despliegue respecto del otro repo

1. **API primero** (agrega un código `429` nuevo; el frontend lo muestra en su enmienda 19-A).
2. **Antes de desplegar la API**, confirmar en F0 que el servicio de producción define
   `JWT_SECRET` (>= 32 bytes) y `DB_PASSWORD`. Si faltan, **la API no arranca**.
3. Hasta hoy producción pudo estar firmando tokens con la clave literal `${JWT_SECRET}`: al
   desplegar con un secreto real, los tokens vigentes dejan de servir (los usuarios vuelven a
   iniciar sesión).
4. Detrás de un proxy, el contador de IP es compartido por todos los usuarios: si se despliega antes
   de la enmienda 06-A, subir `APP_SECURITY_LOGINRATELIMIT_MAXATTEMPTSPERIP` (propiedad
   `app.security.login-rate-limit.max-attempts-per-ip`) o resolver F0 primero.
5. Swagger deja de estar disponible en producción (`401`/`404`); quien lo use debe correr con `dev`.
