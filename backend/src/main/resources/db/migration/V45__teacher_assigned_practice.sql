-- Teacher follow-up on a skill (X10): a class's teacher or principal places practice for a skill
-- on a member's path in the class's curriculum. The path item says which class placed it, who,
-- and for which skill, so that:
--   * the family sees who asked for it and why ("Grade 1 A's teacher · to practise Add within 10");
--   * when the family ends the school's access, or the child leaves the class, that class's
--     waiting practice is withdrawn (finished practice and its evidence stay);
--   * the class sees what it assigned and what happened afterwards.
-- Only TEACHER_ASSIGNED items carry these (the item source existed since V28; nothing created
-- such items until now). Existing rows are untouched.

ALTER TABLE learning_path_items
    ADD COLUMN assigned_class_id UUID,
    ADD COLUMN assigned_by_account_id UUID,
    ADD COLUMN assigned_skill_id UUID;

ALTER TABLE learning_path_items
    ADD CONSTRAINT fk_learning_path_items_assigned_class
        FOREIGN KEY (assigned_class_id) REFERENCES school_classes (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_learning_path_items_assigned_by
        FOREIGN KEY (assigned_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_learning_path_items_assigned_skill
        FOREIGN KEY (assigned_skill_id) REFERENCES skills (id) ON DELETE RESTRICT;

-- A teacher's placement is all three or none, and only on TEACHER_ASSIGNED items. NOT VALID: rows
-- written before this migration are not re-checked (none are expected; this never blocks startup
-- on existing data), every new or updated row is.
ALTER TABLE learning_path_items
    ADD CONSTRAINT ck_learning_path_items_teacher_assignment CHECK (
        (source = 'TEACHER_ASSIGNED'
            AND assigned_class_id IS NOT NULL AND assigned_by_account_id IS NOT NULL AND assigned_skill_id IS NOT NULL)
        OR (source <> 'TEACHER_ASSIGNED'
            AND assigned_class_id IS NULL AND assigned_by_account_id IS NULL AND assigned_skill_id IS NULL)
    ) NOT VALID;

-- The class's assignments: its follow-up view and withdrawal on access changes.
CREATE INDEX ix_learning_path_items_assigned_class ON learning_path_items (assigned_class_id)
    WHERE assigned_class_id IS NOT NULL;
