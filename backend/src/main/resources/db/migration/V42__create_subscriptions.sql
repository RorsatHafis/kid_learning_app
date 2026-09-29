-- Subscriptions and entitlements (PLATFORM-EXPANSION.md §3). A family is the subscriber; a plan
-- carries entitlements (the product's levers); a family's effective plan is resolved at read
-- time from its subscription, or the platform's default plan. Prices, plan names and limits are
-- the owner's decision (PD-09): nothing here assumes them. No payment provider exists yet; the
-- provider columns are where one records its reference when it is integrated.

CREATE TABLE subscription_plans (
    id UUID PRIMARY KEY,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(20) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    -- Whether families may choose it themselves once a payment provider exists.
    self_service BOOLEAN NOT NULL DEFAULT FALSE,
    price_amount_minor BIGINT,
    price_currency VARCHAR(3),
    billing_interval VARCHAR(10),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_subscription_plans_code UNIQUE (code),
    CONSTRAINT ck_subscription_plans_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_subscription_plans_interval CHECK (billing_interval IS NULL OR billing_interval IN ('MONTH', 'YEAR')),
    -- A price is all three parts or none (no price is the owner's decision yet).
    CONSTRAINT ck_subscription_plans_price CHECK (
        (price_amount_minor IS NULL AND price_currency IS NULL AND billing_interval IS NULL)
        OR (price_amount_minor IS NOT NULL AND price_amount_minor >= 0 AND price_currency IS NOT NULL AND billing_interval IS NOT NULL)),
    -- Only an active plan can be the default.
    CONSTRAINT ck_subscription_plans_default_active CHECK (NOT is_default OR status = 'ACTIVE'),
    CONSTRAINT ck_subscription_plans_version CHECK (version >= 0)
);

-- At most one default plan.
CREATE UNIQUE INDEX uq_subscription_plans_default ON subscription_plans (is_default) WHERE is_default;

CREATE TRIGGER trg_subscription_plans_updated_at
    BEFORE UPDATE ON subscription_plans
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE plan_entitlements (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL,
    entitlement_key VARCHAR(60) NOT NULL,
    -- NULL = unlimited.
    limit_value INT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_plan_entitlements_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id) ON DELETE CASCADE,
    CONSTRAINT uq_plan_entitlements_plan_key UNIQUE (plan_id, entitlement_key),
    CONSTRAINT ck_plan_entitlements_key CHECK (entitlement_key IN ('CHILDREN_MAX', 'JOURNEYS_PER_CHILD_MAX')),
    CONSTRAINT ck_plan_entitlements_limit CHECK (limit_value IS NULL OR limit_value >= 0),
    CONSTRAINT ck_plan_entitlements_version CHECK (version >= 0)
);

CREATE TRIGGER trg_plan_entitlements_updated_at
    BEFORE UPDATE ON plan_entitlements
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE family_subscriptions (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    -- How it came about: an administrator's grant today; a payment provider later.
    source VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    -- NULL = open-ended (e.g. a grant without an end date).
    current_period_end TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    provider VARCHAR(40),
    provider_reference VARCHAR(200),
    note VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_family_subscriptions_family FOREIGN KEY (family_id) REFERENCES families (id) ON DELETE RESTRICT,
    CONSTRAINT fk_family_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id) ON DELETE RESTRICT,
    -- One current subscription per family; history lives in the audit log.
    CONSTRAINT uq_family_subscriptions_family UNIQUE (family_id),
    CONSTRAINT ck_family_subscriptions_status CHECK (status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED')),
    CONSTRAINT ck_family_subscriptions_source CHECK (source IN ('ADMIN_GRANT', 'PROVIDER')),
    CONSTRAINT ck_family_subscriptions_provider CHECK (source <> 'PROVIDER' OR (provider IS NOT NULL AND provider_reference IS NOT NULL)),
    CONSTRAINT ck_family_subscriptions_version CHECK (version >= 0)
);

CREATE INDEX ix_family_subscriptions_plan_id ON family_subscriptions (plan_id);

CREATE TRIGGER trg_family_subscriptions_updated_at
    BEFORE UPDATE ON family_subscriptions
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- The platform's default: everything included, as today. The owner replaces or edits it by
-- making another plan the default; no family loses anything until they do.
INSERT INTO subscription_plans (id, code, name, description, status, is_default, self_service)
VALUES ('6f1c2a60-0000-4000-8000-000000000001', 'EARLY_ACCESS', 'Early access',
        'Every part of Bookeyond Kid is included while it is in early access.', 'ACTIVE', TRUE, FALSE);

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, limit_value) VALUES
    ('6f1c2a60-0000-4000-8000-000000000002', '6f1c2a60-0000-4000-8000-000000000001', 'CHILDREN_MAX', NULL),
    ('6f1c2a60-0000-4000-8000-000000000003', '6f1c2a60-0000-4000-8000-000000000001', 'JOURNEYS_PER_CHILD_MAX', NULL);
