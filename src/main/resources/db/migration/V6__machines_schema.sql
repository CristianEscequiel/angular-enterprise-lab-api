-- Spec 02 — máquinas y árbol de partes (REQ-33).
--
-- Sin ON DELETE CASCADE en ninguna FK: borrar una máquina con partes o una
-- parte con hijos tiene que fallar en la base, aunque el dominio se adelante
-- con un 409 legible.

CREATE TABLE machines (
    id    BIGSERIAL    PRIMARY KEY,
    code  VARCHAR(20)  NOT NULL,
    name  VARCHAR(100) NOT NULL,
    CONSTRAINT machines_code_key    UNIQUE (code),
    CONSTRAINT machines_code_format CHECK (code ~ '^[A-Z0-9][A-Z0-9-]{0,19}$')
);

CREATE TABLE parts (
    id          BIGSERIAL    PRIMARY KEY,
    machine_id  BIGINT       NOT NULL,
    parent_id   BIGINT,
    name        VARCHAR(100) NOT NULL,
    CONSTRAINT parts_machine_id_fkey FOREIGN KEY (machine_id) REFERENCES machines (id),
    -- Destino de la FK compuesta de abajo.
    CONSTRAINT parts_id_machine_key UNIQUE (id, machine_id),
    -- Con MATCH SIMPLE una parte sin padre (parent_id NULL) no se comprueba; con
    -- padre, exige que exista y que sea de la misma máquina.
    CONSTRAINT parts_parent_same_machine_fkey
        FOREIGN KEY (parent_id, machine_id) REFERENCES parts (id, machine_id)
);

CREATE INDEX idx_parts_machine ON parts (machine_id);
CREATE INDEX idx_parts_parent  ON parts (parent_id);
