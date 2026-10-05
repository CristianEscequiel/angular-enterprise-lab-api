---
inclusion: always
---

# Stack tecnológico

## Frontend (consumidor, repo separado)
`angular-enterprise-lab` — Angular 22, standalone components, Signals,
RxJS, `core/shared/features`. No se toca desde este repo; los contratos de
API se acuerdan por spec y se documentan en OpenAPI.

## Backend
- Java 21 (LTS), Spring Boot 3.x.
- Gradle (Kotlin DSL o Groovy — se define en la primera tarea de setup).
- Spring Web (REST), Spring Data JPA + Hibernate, Spring Security + JJWT
  (o `spring-security-oauth2-resource-server` en modo JWT local).
- PostgreSQL como base de datos única (dev y prod vía Docker).
- Flyway para migraciones versionadas (no `ddl-auto: update` ni scripts
  sueltos tipo `data.sql`).
- Bean Validation (`jakarta.validation`) en DTOs de entrada.
- springdoc-openapi para Swagger/OpenAPI autogenerado desde el código.
- Testing: JUnit 5, Mockito para unitarios; `@SpringBootTest` +
  Testcontainers (Postgres real) para integración.

## Infraestructura
- Docker + `docker-compose.yaml` para Postgres local (siguiendo el patrón
  que ya usaste en `agenda-spring`).
- CI: GitHub Actions — build + test en cada PR (se especifica como su
  propia tarea, no bloquea el arranque del proyecto).
- Despliegue: se integra más adelante al `web-stack-infrastructure`
  existente (Docker Compose), como su propio servicio.

## Convenciones de código
- Arquitectura por capas `web / domain / persistence`, con el patrón
  puerto-adaptador que ya usaste en `agenda-spring`: interfaces de
  repositorio en `domain`, implementación JPA en `persistence`, sin que
  `domain` dependa de anotaciones de Spring Data ni de JPA.
- DTOs de entrada/salida separados de las entities; mapeo explícito
  (MapStruct o mappers manuales — se decide en `design.md`).
- Manejo de errores centralizado con `@RestControllerAdvice`, siguiendo el
  patrón de `RestExceptionHandler` que ya armaste.
- Un módulo de dominio por feature del frontend (`auth`, `workorders`,
  `maintenance`), no una sola carpeta gigante de entities.

## Orden de evaluación de errores HTTP
Fijado en la spec 00 y confirmado en la spec 01 — aplica a todo endpoint
protegido de cualquier spec futura (`2xx`, `3xx`, `4xx`), no hace falta
redecidirlo por spec:

1. **`401`** — sin token, token inválido, expirado o mal firmado (filtro de
   seguridad, antes de llegar al controller).
2. **`403`** — token válido pero el rol no tiene permiso sobre la
   operación, incluso si además hay un problema de formato en la request
   (el chequeo de rol no espera a que la request esté bien formada para
   negarse).
3. **`400`** — request de un usuario autorizado pero mal formada
   (validación de campos, formato de un identificador en la URL o el
   body).
4. **`404`** — request bien formada y autorizada, pero el recurso no
   existe.
5. **`409`** — request válida, autorizada, sobre un recurso que existe,
   pero la operación viola una regla de integridad (duplicado, referencia
   inexistente, recurso en uso).

Un formato de identificador inválido en la URL (ej. un `legajo` con
letras) nunca cae en `404` — es `400`, salvo que un rol sin permiso lo
intercepte antes con `403` (regla 2).

## Restricciones técnicas
- El contrato de API debe poder ser consumido sin cambios de arquitectura
  grandes en `WorkOrdersService`, `TechniciansService` y `TeamsService`
  del frontend (mismos verbos HTTP y forma de paginación que hoy ofrece
  JSON Server, salvo que una spec decida cambiarlo explícitamente).
- Sin librerías pagas. Sin dependencia de servicios cloud específicos para
  correr en local (todo debe levantar con `docker compose up`).
