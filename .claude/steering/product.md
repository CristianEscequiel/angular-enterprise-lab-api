---
inclusion: always
---

# Producto

## Qué es
API REST que reemplaza a JSON Server como backend de `angular-enterprise-lab`:
un sistema de gestión de órdenes de mantenimiento con roles, técnicos y
equipos. Es la evolución planificada del laboratorio de arquitectura Angular
hacia un stack fullstack real (Angular + Spring Boot + PostgreSQL).

## Usuarios objetivo
Los mismos cuatro roles ya definidos en el frontend: `administrador`,
`team-leader-mantenimiento`, `personal-produccion`, `tecnico`. Sin usuarios
externos ni multi-tenant: es un sistema interno de una organización.

## Objetivos de negocio / del proyecto
- Portfolio: demostrar capacidad fullstack real (no solo consumir una API
  mock) para posicionarse como candidato a roles Angular + Java/Spring en
  el mercado argentino, incluyendo banca/fintech.
- Migrar las reglas de negocio que hoy viven solo en el cliente (permisos,
  validaciones de técnicos/equipos) a un backend con autoridad real —
  el propio README del frontend documenta que hoy "el rol vive en
  localStorage y es editable" y que la autorización real corresponde al
  backend. Ese es el problema concreto que este proyecto resuelve.
- Servir como material de estudio y referencia técnica, igual que
  `angular-enterprise-lab`: decisiones documentadas, specs versionadas.

## Features clave (alcance del backend, en orden de dependencia)
1. Autenticación JWT real + roles (reemplaza el login simulado).
2. CRUD de órdenes de trabajo con las mismas reglas de permiso por rol y
   tipo de orden que ya existen en el frontend.
3. Maestro de técnicos y equipos, con las mismas validaciones cruzadas
   (legajo único, no eliminar técnico referenciado, etc.).
4. Indicadores de dashboard (hoy pendiente también en el frontend).

## Fuera de alcance (por ahora)
- Multi-tenant o multi-organización.
- Notificaciones push/email.
- Asignación automática de órdenes a técnicos por IA (queda como evolución
  futura documentada, no parte de este backend base).
- Migrar `mi-catalogo-online` o `agenda-spring` — son proyectos aparte.
