-- PD-01 (PLATFORM-EXPANSION.md §4): a family gives a school access to a child's learning.
-- Three explicit relationships (CLAUDE.md §15): a class's join code (identifies a class, grants
-- nothing), the family's grant to the school, and the curricula a class assigned (what the school
-- may see, and what a child joining later is enrolled in). class_enrollments counts only while the
-- child's grant for that school is ACTIVE; older rows without one (TD-14) are ignored by every read.

CREATE TABLE class_join_codes (
    id UUID PRIMARY KEY,
    school_class_id UUID NOT NULL,
    code VARCHAR(8) NOT NULL,
    created_by_account_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_class_join_codes_class FOREIGN KEY (school_class_id) REFERENCES school_classes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_join_codes_creator FOREIGN KEY (created_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    -- Unambiguous when read aloud or copied from a board: no 0/O, 1/I/L.
    CONSTRAINT ck_class_join_codes_code CHECK (code ~ '^[A-HJKMNP-Z2-9]{8}$'),
    CONSTRAINT uq_class_join_codes_code UNIQUE (code),
    CONSTRAINT ck_class_join_codes_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_class_join_codes_version CHECK (version >= 0)
);

-- A class has at most one code that hasn't been revoked; making a new one revokes the old.
CREATE UNIQUE INDEX uq_class_join_codes_one_open ON class_join_codes (school_class_id) WHERE revoked_at IS NULL;

CREATE TRIGGER trg_class_join_codes_updated_at
    BEFORE UPDATE ON class_join_codes
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE school_child_access (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    child_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    granted_by_account_id UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_by_account_id UUID,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_school_child_access_school FOREIGN KEY (school_id) REFERENCES schools (id) ON DELETE RESTRICT,
    CONSTRAINT fk_school_child_access_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT fk_school_child_access_granted_by FOREIGN KEY (granted_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_school_child_access_revoked_by FOREIGN KEY (revoked_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_school_child_access_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_school_child_access_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL AND revoked_by_account_id IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_by_account_id IS NOT NULL)
    ),
    CONSTRAINT ck_school_child_access_version CHECK (version >= 0)
);

-- One live grant per school and child; a grant after a revocation is a new row, so history stays.
CREATE UNIQUE INDEX uq_school_child_access_active ON school_child_access (school_id, child_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_school_child_access_child_id ON school_child_access (child_id);

CREATE TRIGGER trg_school_child_access_updated_at
    BEFORE UPDATE ON school_child_access
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE class_curriculum_assignments (
    id UUID PRIMARY KEY,
    school_class_id UUID NOT NULL,
    curriculum_id UUID NOT NULL,
    assigned_by_account_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_class_curriculum_assignments_class FOREIGN KEY (school_class_id) REFERENCES school_classes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_curriculum_assignments_curriculum FOREIGN KEY (curriculum_id) REFERENCES curriculums (id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_curriculum_assignments_by FOREIGN KEY (assigned_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT uq_class_curriculum_assignments_pair UNIQUE (school_class_id, curriculum_id),
    CONSTRAINT ck_class_curriculum_assignments_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT ck_class_curriculum_assignments_version CHECK (version >= 0)
);

CREATE TRIGGER trg_class_curriculum_assignments_updated_at
    BEFORE UPDATE ON class_curriculum_assignments
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
