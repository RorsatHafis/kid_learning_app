-- School staff: which accounts work at which school (CLAUDE.md §15). Distinct from class
-- assignment (teacher_assignments): a teacher first belongs to a school, and only then can
-- be assigned to that school's classes. A principal invites; the teacher accepts.
CREATE TABLE school_staff (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    account_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    invited_by_account_id UUID,
    invited_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_school_staff_school FOREIGN KEY (school_id) REFERENCES schools (id) ON DELETE RESTRICT,
    CONSTRAINT fk_school_staff_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_school_staff_invited_by FOREIGN KEY (invited_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    -- One row per person per school; re-inviting reuses it.
    CONSTRAINT uq_school_staff_school_account UNIQUE (school_id, account_id),
    CONSTRAINT ck_school_staff_status CHECK (status IN ('INVITED', 'ACTIVE', 'DECLINED', 'REMOVED')),
    CONSTRAINT ck_school_staff_version CHECK (version >= 0)
);

CREATE INDEX ix_school_staff_account_id ON school_staff (account_id);

CREATE TRIGGER trg_school_staff_updated_at
    BEFORE UPDATE ON school_staff
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- Teachers already assigned to a class were, in effect, working at that class's school:
-- record them as active staff so the new rule (assign only a school's own staff) changes
-- nothing for them.
INSERT INTO school_staff (id, school_id, account_id, status, invited_by_account_id, invited_at, responded_at)
SELECT gen_random_uuid(), c.school_id, a.teacher_account_id, 'ACTIVE', NULL, min(a.created_at), min(a.created_at)
FROM teacher_assignments a
JOIN school_classes c ON c.id = a.school_class_id
WHERE a.status = 'ACTIVE'
GROUP BY c.school_id, a.teacher_account_id;

-- A link that lets a person invited to a school choose their first password.
ALTER TABLE account_action_tokens DROP CONSTRAINT ck_account_action_tokens_purpose;
ALTER TABLE account_action_tokens ADD CONSTRAINT ck_account_action_tokens_purpose
    CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'ACCOUNT_SETUP'));
