-- Spec 02 — seed de máquinas y partes de desarrollo (REQ-34). Solo se carga en
-- el perfil "dev" (application.yml suma db/seed a spring.flyway.locations ahí
-- y solo ahí): nunca debe llegar a un entorno productivo.
--
-- Replica "maquinas" y "partes" de db.json del frontend CONSERVANDO sus ids
-- (máquinas 1 a 3, partes 1 a 10): las órdenes de prueba de la spec 03 los
-- referencian. Los padres se insertan antes que sus hijos porque la FK
-- compuesta se evalúa fila por fila.

INSERT INTO machines (id, code, name) VALUES
    (1, 'ENV-01', 'Envasadora línea 1'),
    (2, 'SEL-02', 'Selladora'),
    (3, 'ROT-03', 'Rotuladora');

INSERT INTO parts (id, machine_id, parent_id, name) VALUES
    (1,  1, NULL, 'Mesa de transporte'),
    (2,  1, 1,    'Cinta 1'),
    (3,  1, 2,    'Motor de cinta'),
    (4,  1, 3,    'Rodamiento delantero'),
    (5,  1, 1,    'Cinta 2'),
    (6,  1, NULL, 'Cabezal de sellado'),
    (7,  1, 6,    'Resistencia'),
    (8,  2, NULL, 'Cabezal térmico'),
    (9,  2, 8,    'Resistencia'),
    (10, 2, NULL, 'Mordaza');

-- BIGSERIAL no avanza solo con ids explícitos: los próximos altas deben ser 4 y 11.
SELECT setval(pg_get_serial_sequence('machines', 'id'), (SELECT MAX(id) FROM machines));
SELECT setval(pg_get_serial_sequence('parts', 'id'), (SELECT MAX(id) FROM parts));
