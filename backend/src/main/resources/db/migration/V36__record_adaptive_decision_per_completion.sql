-- Battle 5D: the server makes the adaptive decision when an attempt completes
-- (previously only when a client called /adaptive-next afterwards). The decision is
-- recorded once per completed attempt in the existing, so far unused,
-- adaptation_decisions table, so that:
--   * a retried or concurrent completion can never produce a second decision -
--     enforced here, not only by application code;
--   * the compatibility read endpoint can return the decision that was actually
--     made, rather than recomputing it from state that may have moved on.
--
-- Additive only: two nullable columns, their foreign keys and a unique constraint.
-- The table has never been written to, so there is no data to migrate. Rows stay
-- immutable (trg_adaptation_decisions_immutable).

ALTER TABLE adaptation_decisions
    ADD COLUMN activity_attempt_id UUID,
    ADD COLUMN learning_path_item_id UUID;

ALTER TABLE adaptation_decisions
    ADD CONSTRAINT fk_adaptation_decisions_activity_attempt
        FOREIGN KEY (activity_attempt_id) REFERENCES activity_attempts (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_adaptation_decisions_learning_path_item
        FOREIGN KEY (learning_path_item_id) REFERENCES learning_path_items (id) ON DELETE RESTRICT,
    -- PostgreSQL treats NULLs as distinct, so decisions made outside a completion
    -- (none exist today) are unaffected.
    ADD CONSTRAINT uq_adaptation_decisions_activity_attempt UNIQUE (activity_attempt_id);
