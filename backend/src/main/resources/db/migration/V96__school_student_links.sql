-- M12 (owner decision 2026-11-11): a school's student record is linked to a family's child ONLY with the parent or
-- guardian's explicit consent. The school asks (for a child already in the class whose family shares them with the
-- school); a parent connects or declines; either side can disconnect. A link joins two records - nothing is copied or
-- merged, and it ends when the family withdraws the school's access.
CREATE TABLE school_student_links (
    id           UUID PRIMARY KEY,
    school_id    UUID        NOT NULL REFERENCES schools (id) ON DELETE RESTRICT,
    student_id   UUID        NOT NULL REFERENCES school_students (id) ON DELETE RESTRICT,
    child_id     UUID        NOT NULL REFERENCES children (id) ON DELETE RESTRICT,
    status       VARCHAR(16) NOT NULL,
    requested_by UUID        REFERENCES accounts (id) ON DELETE SET NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- The parent or guardian who connected or declined, and when (the consent record).
    decided_by   UUID        REFERENCES accounts (id) ON DELETE SET NULL,
    decided_at   TIMESTAMPTZ,
    ended_by     UUID        REFERENCES accounts (id) ON DELETE SET NULL,
    ended_at     TIMESTAMPTZ,
    CONSTRAINT ck_school_student_links_status CHECK (status IN ('PENDING', 'ACTIVE', 'DECLINED', 'ENDED')),
    -- Connected or declined: when the parent decided is always kept (who, until that account is erased).
    CONSTRAINT ck_school_student_links_decided CHECK (status NOT IN ('ACTIVE', 'DECLINED') OR decided_at IS NOT NULL)
);
-- One open request or link per school record, and per child at a school.
CREATE UNIQUE INDEX uq_school_student_links_student ON school_student_links (student_id) WHERE status IN ('PENDING', 'ACTIVE');
CREATE UNIQUE INDEX uq_school_student_links_child ON school_student_links (school_id, child_id) WHERE status IN ('PENDING', 'ACTIVE');
CREATE INDEX ix_school_student_links_child ON school_student_links (child_id);
