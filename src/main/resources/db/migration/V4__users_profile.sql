-- Enmienda 00-A — nombre visible y correo del usuario (design.md §10.2).
--
-- Las columnas nacen NULABLES a propósito: en dev ya hay usuarios (los cinco que
-- sembró V1_1) que esta migración no puede completar sin inventar datos. El
-- seed V4_1 (solo dev) los completa y V5 hace las columnas obligatorias.

ALTER TABLE users
    ADD COLUMN display_name VARCHAR(100),
    ADD COLUMN email        VARCHAR(254);
