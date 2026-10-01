-- Spec 03 — órdenes de trabajo (REQ-43).
--
-- machine_id y part_id son columnas sueltas, SIN clave foránea hacia machines ni
-- parts: una máquina o una parte referenciada por una orden se puede eliminar y
-- la orden conserva su breadcrumb como historia (REQ-30). Por la misma razón el
-- breadcrumb, el nombre del dueño y el del autor de cierre se guardan como texto.
--
-- Las columnas de takenBy y closingNote las escribe la spec 04; esta spec solo las
-- lee (y las carga el seed de dev). Las invariantes entre ellas y el estado las
-- define la spec 04 junto con las transiciones que las mantienen.

CREATE TABLE work_orders (
    id                   BIGSERIAL     PRIMARY KEY,
    title                VARCHAR(150)  NOT NULL,
    description          VARCHAR(2000) NOT NULL,
    machine_id           BIGINT        NOT NULL,
    part_id              BIGINT,
    -- Sin profundidad máxima en el árbol de partes: TEXT, no VARCHAR.
    breadcrumb           TEXT          NOT NULL,
    machine_comment      VARCHAR(200)  NOT NULL DEFAULT '',
    type                 VARCHAR(20)   NOT NULL,
    priority             VARCHAR(10)   NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    taken_by_id          BIGINT,
    taken_by_name        VARCHAR(100),
    taken_at             TIMESTAMPTZ,
    closing_comment      VARCHAR(500),
    closing_author_id    BIGINT,
    closing_author_name  VARCHAR(100),
    closed_at            TIMESTAMPTZ,
    CONSTRAINT work_orders_type_check
        CHECK (type IN ('preventivo', 'correctivo', 'pronto-intervencion')),
    CONSTRAINT work_orders_priority_check
        CHECK (priority IN ('low', 'medium', 'high')),
    CONSTRAINT work_orders_status_check
        CHECK (status IN ('pending', 'in-progress', 'completed', 'cancelled')),
    CONSTRAINT work_orders_taken_by_id_fkey
        FOREIGN KEY (taken_by_id) REFERENCES users (id),
    CONSTRAINT work_orders_closing_author_id_fkey
        FOREIGN KEY (closing_author_id) REFERENCES users (id)
);
