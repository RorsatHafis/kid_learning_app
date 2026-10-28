-- M12 B0 (2026-10-28): multi-branch schools. A school organization (a school or a group of schools) has one or more
-- branches; the existing `schools` row is the branch - classes, academic years, staff, codes, plan and payments stay
-- on it, as they are. Student records (P2) will belong to the organization; enrolments, attendance, results and fees
-- to a branch and a period.
--
-- Backward compatible: every existing school becomes the only branch of its own organization (same name, its
-- principal as the organization's owner). A school inserted without an organization (any older code path, raw SQL)
-- gets its own one-branch organization from the trigger below, so a branch never lacks one.

CREATE TABLE school_organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    owner_account_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_school_organizations_owner FOREIGN KEY (owner_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_school_organizations_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT ck_school_organizations_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_school_organizations_version CHECK (version >= 0)
);

CREATE INDEX ix_school_organizations_owner ON school_organizations (owner_account_id);

CREATE TRIGGER trg_school_organizations_updated_at
    BEFORE UPDATE ON school_organizations
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

ALTER TABLE schools ADD COLUMN organization_id UUID;

-- One organization per existing school, keyed by the school's id so the backfill is a plain join.
INSERT INTO school_organizations (id, name, owner_account_id, status, created_at, updated_at)
SELECT id, name, principal_account_id, status, created_at, updated_at FROM schools;
UPDATE schools SET organization_id = id;

ALTER TABLE schools ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE schools ADD CONSTRAINT fk_schools_organization
    FOREIGN KEY (organization_id) REFERENCES school_organizations (id) ON DELETE RESTRICT;
CREATE INDEX ix_schools_organization ON schools (organization_id);

CREATE FUNCTION assign_school_organization() RETURNS trigger AS $$
BEGIN
    IF NEW.organization_id IS NULL THEN
        NEW.organization_id := NEW.id;
        INSERT INTO school_organizations (id, name, owner_account_id) VALUES (NEW.id, NEW.name, NEW.principal_account_id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_schools_assign_organization
    BEFORE INSERT ON schools
    FOR EACH ROW
    EXECUTE FUNCTION assign_school_organization();
