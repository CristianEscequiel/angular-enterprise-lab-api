---
inclusion: always
---

# Estructura del proyecto

## Organización de carpetas
```
angular-enterprise-lab-api/
├── .claude/
│   ├── steering/              # este contexto (product/tech/structure)
│   └── specs/<n>-<nombre>/    # requirements.md, design.md, tasks.md (ROADMAP.md: mapa de specs)
├── src/main/java/com/enterpriselab/api/
│   ├── auth/
│   │   ├── web/                # AuthController, DTOs de request/response
│   │   ├── domain/              # UserRepository (interfaz), servicios, excepciones
│   │   ├── persistence/         # UserEntity, JPA repository, mapper
│   │   └── security/            # SecurityConfig, JwtConfig, PasswordEncoder config
│   ├── workorders/
│   │   ├── web/ | domain/ | persistence/
│   ├── maintenance/            # técnicos + equipos, misma razón que en el frontend
│   │   ├── web/ | domain/ | persistence/
│   ├── machines/               # máquinas + árbol de partes (feature propia en el frontend)
│   │   ├── web/ | domain/ | persistence/
│   ├── dashboard/              # indicadores: lecturas agregadas sobre workorders
│   │   ├── web/ | domain/ | persistence/
│   ├── shared/                  # RestExceptionHandler global, tipos de error, paginación
│   └── ApiApplication.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/            # V1__init.sql, V2__..., Flyway
├── src/test/java/...             # misma estructura que main, unitarios + integración
├── docker-compose.yaml           # Postgres local
└── build.gradle(.kts)
```

## Convención de nombres
- Paquetes en minúsculas sin guiones, por feature (`auth`, `workorders`,
  `maintenance`), no por tipo de archivo a nivel raíz.
- Entities: `<Nombre>Entity` (ej. `WorkOrderEntity`), igual que en
  `agenda-spring`. DTOs: `<Nombre>Request` / `<Nombre>Response`, no
  `<Nombre>Dto` genérico, para que el contrato de entrada/salida sea
  explícito en el nombre.
- Migraciones Flyway: `V<n>__<descripcion_snake_case>.sql`.
- Tests: mismo nombre de la clase bajo test + `Test` (unitario) o `IT`
  (integración con Testcontainers).

## Patrones de import
- Sin wildcard imports.
- `domain` no importa nada de `persistence` ni de `org.springframework.data.*`
  ni de `jakarta.persistence.*` — es la regla que hace valer la separación
  puerto/adaptador. Si un tipo de dominio necesita anotaciones de JPA, esa
  es una señal de que se está filtrando la capa de persistencia hacia arriba.

## Decisiones de arquitectura
- Organización por feature, no por tipo de archivo — mismo criterio que ya
  aplicás en el frontend (`features/work-orders/`, `features/maintenance/`).
- Capas `web / domain / persistence` por feature, replicando y consolidando
  el patrón de `agenda-spring` (que lo tenía a nivel de proyecto entero, acá
  se repite por módulo).
- La lógica de autorización por rol vive en `domain` (servicios), no en los
  controllers ni en anotaciones sueltas — los controllers exponen HTTP, los
  servicios deciden qué está permitido. Refleja lo que ya hiciste en
  `work-order.permissions.ts` del frontend, ahora del lado que sí tiene
  autoridad real.
