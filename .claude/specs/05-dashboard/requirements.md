# Spec 05 — Dashboard: indicadores

## Contexto y decisiones

Sexta spec del backend, módulo `dashboard`. **Es una propuesta mía a validar**: la
página `/dashboard` del frontend hoy es una pantalla vacía ("todavía sin
indicadores"), su roadmap solo dice "desarrollar los indicadores del dashboard" y
ninguna de sus specs los define. `steering/product.md` lo lista como cuarta
función del backend ("indicadores de dashboard, hoy pendiente también en el
frontend"). Los indicadores de abajo salen de los datos que ya existen (estado,
prioridad, tipo, dueño y cierre de las órdenes) y de lo que se le suele pedir a
un tablero de mantenimiento; cada uno se puede quitar o cambiar al revisar. Ver
`.claude/specs/ROADMAP.md`.

Decisiones tomadas para esta spec:

- Solo lectura: dos endpoints `GET`, sin escrituras ni tablas nuevas de datos
  propios; todo se calcula sobre `work_orders` (specs 03 y 04).
- `GET /dashboard/summary`: para **cualquier usuario autenticado** (el dashboard
  es la pantalla de inicio de los cuatro roles).
- `GET /dashboard/workload`: para `administrador` y `team-leader-mantenimiento`,
  porque muestra qué técnico tiene cuántas órdenes.
- **Período**: `from` y `to`, fechas `YYYY-MM-DD` en UTC, ambas incluidas. Por
  defecto, los últimos 30 días hasta hoy. Solo afecta a lo que se mide por fecha
  de cierre (`closedInPeriod` y `averageResolutionMinutes`); los conteos por
  estado, prioridad y tipo son sobre **todas** las órdenes.
- **Tiempo de resolución** = `closingNote.at` menos `createdAt`, en minutos,
  solo de órdenes `completed` (una cancelada no se resolvió).
- Sin caché: cada consulta refleja el estado actual.
- Sin token: `401`; sin permiso: `403`; parámetros inválidos: `400` con el
  `ApiError` de la spec 00. Los `code` de error no cambian.

Forma de la respuesta de `summary` (fija los nombres que usan los requisitos):

```json
{
  "period": { "from": "2026-08-31", "to": "2026-09-29" },
  "byStatus": { "pending": 12, "in-progress": 9, "completed": 9, "cancelled": 2 },
  "byPriority": { "low": 0, "medium": 0, "high": 0 },
  "byType": { "preventivo": 0, "correctivo": 0, "pronto-intervencion": 0 },
  "total": 32,
  "open": 21,
  "closedInPeriod": { "completed": 0, "cancelled": 0, "total": 0 },
  "averageResolutionMinutes": null
}
```

Alcance descartado explícitamente para esta spec:

- Gráficos, exportación a archivo y tendencias históricas por día o semana.
- Indicadores por máquina, por parte o por equipo (las órdenes se pueden filtrar
  por máquina recién en una spec futura, 013d lo dejó fuera).
- Metas, alertas y notificaciones (SLA vencidos, órdenes sin tomar hace X horas).
- Indicadores personalizados por usuario o por rol distintos de los dos endpoints.
- Actualización en tiempo real (`push`): el cliente consulta cuando quiere.

## Requisitos

### Resumen

### REQ-1: Resumen del tablero
CUANDO un usuario autenticado solicita `GET /dashboard/summary`
EL SISTEMA DEBERÁ responder `200 OK` con `period`, `byStatus`, `byPriority`,
`byType`, `total`, `open`, `closedInPeriod` y `averageResolutionMinutes`.

### REQ-2: Conteo por estado
CUANDO se solicita el resumen
EL SISTEMA DEBERÁ devolver en `byStatus` la cantidad de órdenes de cada uno de
los cuatro estados, con `0` para los que no tienen ninguna.

### REQ-3: Conteo por prioridad
CUANDO se solicita el resumen
EL SISTEMA DEBERÁ devolver en `byPriority` la cantidad de órdenes de cada una de
las tres prioridades, con `0` para las que no tienen ninguna.

### REQ-4: Conteo por tipo
CUANDO se solicita el resumen
EL SISTEMA DEBERÁ devolver en `byType` la cantidad de órdenes de cada uno de los
tres tipos, con `0` para los que no tienen ninguna.

### REQ-5: Total y abiertas
CUANDO se solicita el resumen
EL SISTEMA DEBERÁ devolver en `total` la cantidad de todas las órdenes y en `open`
la suma de las `pending` y las `in-progress`.

### REQ-6: Coherencia con el listado
CUANDO se solicita el resumen
EL SISTEMA DEBERÁ devolver, para cada estado y cada prioridad, el mismo número que
el `totalItems` de `GET /work-orders?status={estado}` y de
`GET /work-orders?priority={prioridad}` en ese momento.

### REQ-7: Cerradas en el período
CUANDO se solicita el resumen con un período
EL SISTEMA DEBERÁ devolver en `closedInPeriod` cuántas órdenes `completed` y
cuántas `cancelled` tienen su `closingNote.at` dentro del período, con `total`
igual a la suma de ambas, contando el día `from` y el día `to` completos.

### REQ-8: Período por defecto
CUANDO se solicita el resumen sin `from` ni `to`
EL SISTEMA DEBERÁ usar como período los 30 días que terminan hoy en UTC (`to` = hoy
y `from` = hoy menos 29 días, los dos incluidos), y devolverlo en `period`.

### REQ-9: Período con un solo extremo
CUANDO se envía solo `from` o solo `to`
EL SISTEMA DEBERÁ completar el otro extremo: `to` en el día de hoy si falta, y
`from` 29 días antes de `to` si falta.

### REQ-10: Período inválido
CUANDO `from` o `to` no es una fecha `YYYY-MM-DD` válida, o `from` es posterior a
`to`
EL SISTEMA DEBERÁ responder `400 Bad Request` con `code` `VALIDATION_ERROR` y el
detalle del parámetro.

### REQ-11: Tiempo promedio de resolución
CUANDO hay órdenes `completed` cerradas dentro del período
EL SISTEMA DEBERÁ devolver en `averageResolutionMinutes` el promedio, en minutos y
redondeado a un decimal, de `closingNote.at` menos `createdAt` de esas órdenes, y
DEBERÁ no considerar las `cancelled` ni las cerradas fuera del período.

### REQ-12: Sin órdenes resueltas
CUANDO no hay ninguna orden `completed` cerrada dentro del período
EL SISTEMA DEBERÁ devolver `averageResolutionMinutes` en `null`.

### REQ-13: Sin órdenes
CUANDO no existe ninguna orden
EL SISTEMA DEBERÁ responder `200 OK` con todos los conteos en `0` y
`averageResolutionMinutes` en `null`.

### REQ-14: Sin caché
CUANDO una orden cambia de estado por `take`, `close` o `release` (spec 04)
EL SISTEMA DEBERÁ reflejarla en la siguiente consulta del resumen, sin esperar a
que venza ningún intervalo.

### Carga de trabajo

### REQ-15: Órdenes en progreso por técnico
CUANDO un `administrador` o un `team-leader-mantenimiento` solicita
`GET /dashboard/workload`
EL SISTEMA DEBERÁ responder `200 OK` con una lista con un elemento por cada
técnico que tiene órdenes `in-progress`, con `takenById`, `takenByName` y la
cantidad de órdenes `in-progress` a su nombre.

### REQ-16: Orden de la lista
CUANDO se solicita `GET /dashboard/workload`
EL SISTEMA DEBERÁ ordenar la lista por cantidad descendente y, a igual cantidad,
por `takenByName` ascendente.

### REQ-17: Sin órdenes en progreso
CUANDO no hay ninguna orden `in-progress`
EL SISTEMA DEBERÁ responder `200 OK` con una lista vacía.

### Permisos, datos y documentación

### REQ-18: Permiso del resumen
CUANDO un usuario autenticado, con cualquiera de los cuatro roles, solicita
`GET /dashboard/summary`
EL SISTEMA DEBERÁ responder con los datos y no con `403`; y CUANDO no envía token,
EL SISTEMA DEBERÁ responder `401 Unauthorized`.

### REQ-19: Permiso de la carga de trabajo
CUANDO un usuario autenticado con rol `personal-produccion` o `tecnico` solicita
`GET /dashboard/workload`
EL SISTEMA DEBERÁ responder `403 Forbidden`.

### REQ-20: Documentación OpenAPI
CUANDO se accede a la documentación de springdoc
EL SISTEMA DEBERÁ mostrar los endpoints de `/dashboard` generados desde las
anotaciones del código, con el esquema de seguridad `bearerAuth`.

## Auto-revisión

- Un comportamiento por requisito: REQ-18 junta el permiso y el `401` del mismo
  endpoint en dos cláusulas; REQ-11 describe el promedio y sus exclusiones porque
  son la definición del indicador, no dos comportamientos.
- Caminos no felices cubiertos: período inválido (REQ-10), estado vacío (REQ-12,
  REQ-13, REQ-17), sin token y sin permiso (REQ-18, REQ-19).
- Sin adjetivos no verificables: cada indicador se define con una fórmula y un
  redondeo; la coherencia con el listado (REQ-6) es una comparación exacta.
- **Puntos a validar, todos míos** (el frontend no los define):
  1. El conjunto de indicadores: estado, prioridad, tipo, abiertas, cerradas en el
     período, tiempo promedio de resolución y carga por técnico.
  2. Que los conteos por estado, prioridad y tipo sean sobre todas las órdenes y
     no sobre las abiertas.
  3. Los 30 días por defecto y UTC como zona horaria del período.
  4. Que `workload` sea solo de administrador y team leader.
  5. Que una orden cancelada no cuente para el tiempo de resolución.
- Dependencias: las specs 03 y 04 (`work_orders`, `takenBy`, `closingNote`). El
  seed de la spec 03 (32 órdenes con 9 `completed`) da datos para probar.
- Riesgo para el diseño: `createdAt` de las órdenes de prueba viene de `db.json`
  sin zona horaria y `closingNote.at` con `Z` (REQ-44 de la spec 03 los pasa a
  UTC); un promedio negativo en el seed indicaría un dato mal convertido.
