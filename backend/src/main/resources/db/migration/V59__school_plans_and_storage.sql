-- Schools on plans, and storage as a plan entitlement (PLATFORM-EXPANSION.md §3, Y35).
--
-- A plan now has an audience: FAMILY (what a family can add - V42) or SCHOOL (what a school can
-- hold, and how much media each of its teachers can keep). Each audience has its own default plan,
-- which applies whenever a subscription isn't in force. School subscriptions mirror family ones:
-- one current row per school, granted by an administrator today, by a payment provider once one is
-- integrated (PD-09). Nothing here takes anything away: the school default plan seeded below has
-- no limit on children, teachers or classes. It does set a storage allowance per teacher, the one
-- limit that protects the platform's disk (see the policy in PLATFORM-EXPANSION.md §3).

ALTER TABLE subscription_plans
    ADD COLUMN audience VARCHAR(10) NOT NULL DEFAULT 'FAMILY',
    ADD CONSTRAINT ck_subscription_plans_audience CHECK (audience IN ('FAMILY', 'SCHOOL'));

-- At most one default plan per audience (was: at most one overall).
DROP INDEX uq_subscription_plans_default;
CREATE UNIQUE INDEX uq_subscription_plans_default ON subscription_plans (audience) WHERE is_default;

ALTER TABLE plan_entitlements DROP CONSTRAINT ck_plan_entitlements_key;
ALTER TABLE plan_entitlements ADD CONSTRAINT ck_plan_entitlements_key CHECK (entitlement_key IN (
    'CHILDREN_MAX', 'JOURNEYS_PER_CHILD_MAX',
    'SCHOOL_CHILDREN_MAX', 'SCHOOL_TEACHERS_MAX', 'SCHOOL_CLASSES_MAX', 'TEACHER_STORAGE_MB'));

CREATE TABLE school_subscriptions (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    -- NULL = open-ended (a grant without an end date).
    current_period_end TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    provider VARCHAR(40),
    provider_reference VARCHAR(200),
    note VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_school_subscriptions_school FOREIGN KEY (school_id) REFERENCES schools (id) ON DELETE CASCADE,
    CONSTRAINT fk_school_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id) ON DELETE RESTRICT,
    CONSTRAINT uq_school_subscriptions_school UNIQUE (school_id),
    CONSTRAINT ck_school_subscriptions_status CHECK (status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED')),
    CONSTRAINT ck_school_subscriptions_source CHECK (source IN ('ADMIN_GRANT', 'PROVIDER')),
    CONSTRAINT ck_school_subscriptions_version CHECK (version >= 0)
);
CREATE INDEX ix_school_subscriptions_plan ON school_subscriptions (plan_id);

CREATE TRIGGER trg_school_subscriptions_updated_at
    BEFORE UPDATE ON school_subscriptions
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- An administrator's exception to one author's storage allowance (an exceptional project, a pilot):
-- it replaces what their schools' plans give. Every change is audited; NULL limit = unlimited.
CREATE TABLE account_storage_allowances (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    limit_mb INT,
    note VARCHAR(500) NOT NULL,
    set_by_account_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_account_storage_allowances_account UNIQUE (account_id),
    CONSTRAINT fk_account_storage_allowances_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_account_storage_allowances_set_by FOREIGN KEY (set_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_account_storage_allowances_limit CHECK (limit_mb IS NULL OR limit_mb >= 0),
    CONSTRAINT ck_account_storage_allowances_note CHECK (char_length(btrim(note)) > 0),
    CONSTRAINT ck_account_storage_allowances_version CHECK (version >= 0)
);

CREATE TRIGGER trg_account_storage_allowances_updated_at
    BEFORE UPDATE ON account_storage_allowances
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- Usage is summed per owner on every upload (under the owner's lock) and in the views.
CREATE INDEX ix_content_assets_owner_size ON content_assets (owner_account_id) INCLUDE (size_bytes) WHERE sha256 IS NOT NULL;

-- The school default plan: no limit on children, teachers or classes (nothing taken away), and
-- 1024 MB of pictures and recordings per teacher - a recommended starting allowance (a picture is
-- at most 2 MB and a recording 5 MB, so this is several hundred recordings or over a thousand
-- pictures), pending the owner's decision (PD-09). Changing it is done by drafting another plan.
INSERT INTO subscription_plans (id, code, name, description, status, is_default, self_service, audience)
VALUES ('6f1c2a60-0000-4000-8000-000000000100', 'SCHOOL_EARLY_ACCESS', 'School early access',
        'What every school has while schools are in early access: no limit on children, teachers or classes.',
        'ACTIVE', TRUE, FALSE, 'SCHOOL');

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, limit_value) VALUES
    ('6f1c2a60-0000-4000-8000-000000000101', '6f1c2a60-0000-4000-8000-000000000100', 'SCHOOL_CHILDREN_MAX', NULL),
    ('6f1c2a60-0000-4000-8000-000000000102', '6f1c2a60-0000-4000-8000-000000000100', 'SCHOOL_TEACHERS_MAX', NULL),
    ('6f1c2a60-0000-4000-8000-000000000103', '6f1c2a60-0000-4000-8000-000000000100', 'SCHOOL_CLASSES_MAX', NULL),
    ('6f1c2a60-0000-4000-8000-000000000104', '6f1c2a60-0000-4000-8000-000000000100', 'TEACHER_STORAGE_MB', 1024);
