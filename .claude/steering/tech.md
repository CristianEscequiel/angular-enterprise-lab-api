---
inclusion: always
---

# Stack tecnológico

## Frontend (consumidor, repo separado)
`angular-enterprise-lab` — Angular 22, standalone components, Signals,
RxJS, `core/shared/features`. No se toca desde este repo; los contratos de
API se acuerdan por spec y se documentan en OpenAPI. El frontend ya consume
esta API (su spec 19).

## Backend
- Java 21 (LTS), Spring Boot 3.5.
- Gradle con Kotlin DSL (`build.gradle.kts`).
- Spring Web (REST), Spring Data JPA + Hibernate, Spring Security con
  `spring-boot-starter-oauth2-resource-server` (JWT HS256 local, sin JJWT).
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
- CI: GitHub Actions (`.github/workflows/ci.yml`) — build + test en cada PR.
  Pendiente de confirmar que termine en verde en un PR.
- Despliegue: se integra más adelante al `web-stack-infrastructure`
  existente (Docker Compose), como su propio servicio.

## Convenciones de código
- Arquitectura por capas `web / domain / persistence`, con el patrón
  puerto-adaptador que ya usaste en `agenda-spring`: interfaces de
  repositorio en `domain`, implementación JPA en `persistence`, sin que
  `domain` dependa de anotaciones de Spring Data ni de JPA.
- DTOs de entrada/salida separados de las entities; mapeo explícito con
  mappers manuales (sin MapStruct).
- Manejo de errores centralizado con `@RestControllerAdvice`, siguiendo el
  patrón de `RestExceptionHandler` que ya armaste.
- Un módulo de dominio por feature del frontend (`auth`, `workorders`,
  `maintenance`, `machines`, `dashboard`), no una sola carpeta gigante de entities.

## Restricciones técnicas
- Contrato de API: REST limpio en inglés (decidido en `ROADMAP.md`, D1), con
  paginación `page`/`size` y respuesta `{data, page, size, totalItems, totalPages}`.
  Reemplazó el formato de JSON Server; el frontend lo adaptó en su spec 19.
  Cualquier cambio de contrato se acuerda por spec en ambos repos.
- Sin librerías pagas. Sin dependencia de servicios cloud específicos para
  correr en local (todo debe levantar con `docker compose up`).
