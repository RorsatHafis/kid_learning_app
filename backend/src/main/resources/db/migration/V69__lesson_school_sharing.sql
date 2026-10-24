-- M5b (product improvement program, owner decision D-13): a teacher's lesson can be shared with the teachers of
-- their school (SCHOOL). A colleague doesn't use it in place: they copy it into their own content, which credits
-- where it came from. Children's paths still draw only on their curriculum owner's and platform content (N7), so
-- sharing or unsharing never changes a child's path. PUBLIC is deferred (D-14). Platform (ownerless) lessons are
-- never shared this way: they are every family's already.

ALTER TABLE lessons
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    -- The lesson this one was copied from, and its author then (the credit), kept if either goes.
    ADD COLUMN copied_from_lesson_id UUID,
    ADD COLUMN copied_from_account_id UUID,
    ADD CONSTRAINT ck_lessons_visibility CHECK (visibility IN ('PRIVATE', 'SCHOOL')),
    ADD CONSTRAINT ck_lessons_platform_not_shared CHECK (owner_account_id IS NOT NULL OR visibility = 'PRIVATE'),
    ADD CONSTRAINT fk_lessons_copied_from FOREIGN KEY (copied_from_lesson_id) REFERENCES lessons (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_lessons_copied_from_account FOREIGN KEY (copied_from_account_id) REFERENCES accounts (id) ON DELETE SET NULL;

-- Colleagues' shared lessons, by subject.
CREATE INDEX ix_lessons_shared ON lessons (subject_id, owner_account_id) WHERE visibility = 'SCHOOL';
