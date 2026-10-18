-- Y36: a parent may ask the child's space to suggest a break after this many minutes of continuous
-- learning (PRODUCT-MODEL-BRIEF.md section 4). Off (null) until a parent turns it on. The suggestion
-- only ever comes between activities, never in the middle of one, and the child may go on: it is
-- guidance, not a lock. Kept in the existing per-child settings row (V9), created when first set.
ALTER TABLE child_settings
    ADD COLUMN break_reminder_minutes INT,
    ADD CONSTRAINT ck_child_settings_break_reminder
        CHECK (break_reminder_minutes IS NULL OR break_reminder_minutes IN (10, 15, 20, 30, 45));
