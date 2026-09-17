CREATE TABLE memberships (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    account_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_memberships_family
        FOREIGN KEY (family_id) REFERENCES families (id) ON DELETE RESTRICT,
    CONSTRAINT fk_memberships_account
        FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT uq_memberships_family_account
        UNIQUE (family_id, account_id),
    CONSTRAINT ck_memberships_role
        CHECK (role IN ('OWNER', 'GUARDIAN')),
    CONSTRAINT ck_memberships_status
        CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT ck_memberships_version
        CHECK (version >= 0)
);

CREATE INDEX ix_memberships_account_id
    ON memberships (account_id);

CREATE TRIGGER trg_memberships_updated_at
    BEFORE UPDATE ON memberships
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
