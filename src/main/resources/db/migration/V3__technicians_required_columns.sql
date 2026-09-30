-- Spec 01 — endurece technicians una vez que las filas preexistentes están
-- completas (en dev las completa el seed V2_1; fuera de dev no hay filas, la
-- spec 00 no expone ninguna forma de crearlas). Si esta migración falla por una
-- fila incompleta, es el fail-fast buscado (design.md §2.2).

ALTER TABLE technicians
    ALTER COLUMN first_name SET NOT NULL,
    ALTER COLUMN last_name  SET NOT NULL,
    ALTER COLUMN specialty  SET NOT NULL,
    ALTER COLUMN team_type  SET NOT NULL,
    -- Defensa en profundidad: el dominio valida el formato primero (Legajo).
    ADD CONSTRAINT technicians_legajo_format CHECK (legajo ~ '^[0-9]{1,8}$');
