-- Spec 00 — seed de usuarios de desarrollo. Solo se carga en el perfil
-- "dev" (application.yml suma esta ubicación a spring.flyway.locations ahí
-- y solo ahí): nunca debe llegar a un entorno productivo.
--
-- Replica los usuarios de prueba de angular-enterprise-lab
-- (src/app/features/work-orders/data-access/db.json), uno por cada uno de
-- los cuatro roles (con dos "tecnico", igual que en el frontend). Las
-- contraseñas en texto plano están documentadas en el README (tarea 21);
-- acá solo van hasheadas con BCrypt (REQ-6), generadas con el
-- PasswordEncoder de la tarea 8.

INSERT INTO technicians (legajo) VALUES
    ('1001'),
    ('1002');

INSERT INTO users (username, password_hash, role, technician_id) VALUES
    ('admin', '$2a$10$jzWxSmOMgByjdl45Ow90zORZ4Ww4TwMQtgB1Kespq3PwDlcciPXV6', 'administrador', NULL),
    ('teamleader', '$2a$10$WF8kmkAs9pOeu4NOXrogxuWgYr81LUV8YyWFmsGL4/gxzuTz73lry', 'team-leader-mantenimiento', NULL),
    ('produccion', '$2a$10$XjiVLyqJJIfTGBRrdF11hODvV0ZO.werozlmtavclqd2CBOoEz5HC', 'personal-produccion', NULL),
    ('tecnico', '$2a$10$F8nu2bYhtiFKtiiZMJhsG.K80Zgk2dk6oyX.1xQZsRcSOcVHt4.ym', 'tecnico',
        (SELECT id FROM technicians WHERE legajo = '1001')),
    ('electricista', '$2a$10$cNt9XYoG879BICHA41cDZOYkt9nQLJRwaYlcxSUuSkR6t9E1tz.KK', 'tecnico',
        (SELECT id FROM technicians WHERE legajo = '1002'));
