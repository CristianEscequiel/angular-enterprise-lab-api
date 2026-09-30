-- Enmienda 00-A — endurece users una vez que las filas preexistentes están
-- completas (en dev las completa el seed V4_1; fuera de dev no hay filas: la
-- aplicación no expone ninguna forma de crear usuarios). Si esta migración falla
-- por una fila incompleta, es el fail-fast buscado (design.md §10.2).

ALTER TABLE users
    ALTER COLUMN display_name SET NOT NULL,
    ALTER COLUMN email        SET NOT NULL,
    ADD CONSTRAINT users_profile_not_blank
        CHECK (btrim(display_name) <> '' AND btrim(email) <> '');
