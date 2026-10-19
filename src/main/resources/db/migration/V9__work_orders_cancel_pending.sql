-- Enmienda 04-A, REQ-41 y REQ-47: una orden pendiente puede cancelarse sin dueño ni comentario.
-- No modifica filas. work_orders_owner_group_check de V8 queda como está.
ALTER TABLE work_orders
    DROP CONSTRAINT work_orders_closing_group_check,
    DROP CONSTRAINT work_orders_state_invariants_check;

ALTER TABLE work_orders
    ADD CONSTRAINT work_orders_closing_group_check CHECK (
        (closing_author_id IS NULL AND closing_author_name IS NULL
            AND closing_comment IS NULL AND closed_at IS NULL)
        OR (closing_author_id IS NOT NULL AND closing_author_name IS NOT NULL
            AND closed_at IS NOT NULL)),
    ADD CONSTRAINT work_orders_state_invariants_check CHECK (
        (status = 'pending'     AND taken_by_id IS NULL     AND closing_author_id IS NULL)
     OR (status = 'in-progress' AND taken_by_id IS NOT NULL AND closing_author_id IS NULL)
     OR (status IN ('completed', 'cancelled')
         AND taken_by_id IS NOT NULL AND closing_author_id IS NOT NULL
         AND closing_comment IS NOT NULL AND closing_author_id = taken_by_id)
     OR (status = 'cancelled'
         AND taken_by_id IS NULL AND closing_author_id IS NOT NULL
         AND closing_comment IS NULL AND closed_at IS NOT NULL));
