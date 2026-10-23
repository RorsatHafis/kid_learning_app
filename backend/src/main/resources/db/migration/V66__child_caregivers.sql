-- M10b (product improvement program): a trusted caregiver - a grandparent, a nanny - invited by a parent for one
-- child, either to see the child's progress or also to open the child's learning space. A caregiver is not a member
-- of the family: no billing, settings, export, deletion or other children. The parent can change or end it at once.

CREATE TABLE child_caregivers (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    -- The family the child belongs to, for the per-family limit (a child never changes family).
    family_id UUID NOT NULL,
    -- The address the invitation is for; only an account with this address, confirmed, can accept it.
    invited_email VARCHAR(320) NOT NULL,
    -- Set when accepted.
    account_id UUID,
    scope VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    invited_by_account_id UUID NOT NULL,
    invited_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_child_caregivers_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT ck_child_caregivers_scope CHECK (scope IN ('VIEW_PROGRESS', 'OPEN_SPACE')),
    CONSTRAINT ck_child_caregivers_status CHECK (status IN ('INVITED', 'ACTIVE', 'DECLINED', 'REVOKED')),
    CONSTRAINT ck_child_caregivers_accepted CHECK (status <> 'ACTIVE' OR account_id IS NOT NULL)
);

-- One live invitation or caregiver per child and address.
CREATE UNIQUE INDEX uq_child_caregivers_live ON child_caregivers (child_id, invited_email) WHERE status IN ('INVITED', 'ACTIVE');
CREATE INDEX ix_child_caregivers_account ON child_caregivers (account_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_child_caregivers_email ON child_caregivers (invited_email) WHERE status = 'INVITED';
CREATE INDEX ix_child_caregivers_family ON child_caregivers (family_id);
