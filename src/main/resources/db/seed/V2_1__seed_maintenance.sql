-- Spec 01 — seed de técnicos y equipos de desarrollo (REQ-37). Solo se carga en
-- el perfil "dev" (application.yml suma db/seed a spring.flyway.locations ahí
-- y solo ahí): nunca debe llegar a un entorno productivo.
--
-- Corre entre V2 (columnas nulables) y V3 (columnas obligatorias). Replica
-- técnicos y equipos de db.json del frontend.

-- 1001 y 1002 ya existen (V1_1, spec 00) y los usuarios "tecnico" y
-- "electricista" apuntan a esas filas: se COMPLETAN con UPDATE para conservar
-- su id, no se vuelven a insertar.
UPDATE technicians
   SET first_name = 'Ana', last_name = 'Ruiz',
       specialty = 'mecanico', team_type = 'guardia'
 WHERE legajo = '1001';

UPDATE technicians
   SET first_name = 'Luis', last_name = 'Paz',
       specialty = 'electricista', team_type = 'preventivo-correctivo'
 WHERE legajo = '1002';

-- 1003 es nuevo y no tiene usuario de login.
INSERT INTO technicians (legajo, first_name, last_name, specialty, team_type) VALUES
    ('1003', 'Marta', 'Gómez', 'general', 'preventivo-correctivo');

INSERT INTO teams (name, type) VALUES
    ('Guardia mecánica', 'guardia'),
    ('Preventivo eléctrico', 'preventivo-correctivo');

-- Los ids se resuelven por subselect: si faltara un técnico o un equipo el
-- subselect da NULL y falla el NOT NULL de team_members, en vez de dejar un
-- seed a medias.
INSERT INTO team_members (team_id, technician_id, sort_order) VALUES
    ((SELECT id FROM teams WHERE name = 'Guardia mecánica'),
     (SELECT id FROM technicians WHERE legajo = '1001'), 0),
    ((SELECT id FROM teams WHERE name = 'Preventivo eléctrico'),
     (SELECT id FROM technicians WHERE legajo = '1002'), 0);
