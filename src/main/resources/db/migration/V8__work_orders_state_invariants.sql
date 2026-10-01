-- Spec 04, REQ-30: invariantes de estado de una orden. No modifica filas.
ALTER TABLE work_orders
    ADD CONSTRAINT work_orders_owner_group_check CHECK (
        (taken_by_id IS NULL AND taken_by_name IS NULL AND taken_at IS NULL)
        OR (taken_by_id IS NOT NULL AND taken_by_name IS NOT NULL AND taken_at IS NOT NULL)),
    ADD CONSTRAINT work_orders_closing_group_check CHECK (
        (closing_author_id IS NULL AND closing_author_name IS NULL
            AND closing_comment IS NULL AND closed_at IS NULL)
        OR (closing_author_id IS NOT NULL AND closing_author_name IS NOT NULL
            AND closing_comment IS NOT NULL AND closed_at IS NOT NULL)),
    ADD CONSTRAINT work_orders_state_invariants_check CHECK (
        (status = 'pending'     AND taken_by_id IS NULL     AND closing_author_id IS NULL)
     OR (status = 'in-progress' AND taken_by_id IS NOT NULL AND closing_author_id IS NULL)
     OR (status IN ('completed', 'cancelled')
         AND taken_by_id IS NOT NULL AND closing_author_id IS NOT NULL
         AND closing_author_id = taken_by_id));
