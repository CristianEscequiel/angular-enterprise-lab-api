-- Spec 01 — esquema del maestro de mantenimiento (design.md §2.1).
--
-- Las columnas nuevas de technicians nacen NULABLES a propósito: en dev ya
-- hay filas (1001 y 1002, sembradas por V1_1 de la spec 00) que esta
-- migración no puede completar sin inventar datos. El seed V2_1 (solo dev) las
-- completa y V3 hace las columnas obligatorias. Ver design.md §2.2.

ALTER TABLE technicians
    ADD COLUMN first_name VARCHAR(100),
    ADD COLUMN last_name  VARCHAR(100),
    ADD COLUMN specialty  VARCHAR(20)
        CHECK (specialty IN ('mecanico', 'electricista', 'general')),
    ADD COLUMN team_type  VARCHAR(30)
        CHECK (team_type IN ('guardia', 'preventivo-correctivo'));

CREATE TABLE teams (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    type  VARCHAR(30)  NOT NULL
        CHECK (type IN ('guardia', 'preventivo-correctivo'))
);

CREATE TABLE team_members (
    id            BIGSERIAL PRIMARY KEY,
    -- Borrar un equipo borra sus membresías y deja intactos a los técnicos.
    team_id       BIGINT  NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    -- Sin ON DELETE CASCADE (REQ-36): un técnico que es miembro no se borra.
    technician_id BIGINT  NOT NULL REFERENCES technicians (id),
    sort_order    INTEGER NOT NULL,
    CONSTRAINT team_members_team_technician_key UNIQUE (team_id, technician_id)
);

-- La consulta "¿de qué equipos es miembro?" (REQ-16) va por technician_id y el
-- UNIQUE de arriba empieza por team_id, así que no la cubre.
CREATE INDEX idx_team_members_technician ON team_members (technician_id);
