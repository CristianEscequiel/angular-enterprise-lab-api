-- Spec 00 — esquema base: technicians (mínima, solo legajo — la spec de
-- mantenimiento la extiende) y users (design.md §2, §3).

CREATE TABLE technicians (
    id     BIGSERIAL PRIMARY KEY,
    legajo VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(40)  NOT NULL
                   CHECK (role IN ('administrador', 'team-leader-mantenimiento',
                                   'personal-produccion', 'tecnico')),
    technician_id  BIGINT REFERENCES technicians(id),
    -- REQ-5: un usuario tecnico siempre tiene legajo asociado, y solo un
    -- usuario tecnico lo tiene.
    CONSTRAINT users_tecnico_has_legajo
        CHECK ((role = 'tecnico') = (technician_id IS NOT NULL))
);
