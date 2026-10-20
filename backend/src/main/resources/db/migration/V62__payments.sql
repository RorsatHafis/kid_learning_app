-- Y43: payments. A payment order is one attempt to pay for one plan, by one family or for one school.
-- It freezes what was offered when checkout began (plan, price, currency, period, limits), so a later
-- plan edit never changes what was bought. Its state changes only through the payment service, and each
-- change - every callback, confirmation, rejection, expiry and refund - is appended to payment_events,
-- which is never updated. Access is granted only when an order becomes SUCCEEDED (V42/V59 subscriptions).
-- No card details, provider secrets or raw provider payloads are stored here.

CREATE TABLE payment_orders (
    id UUID PRIMARY KEY,
    -- Ours, shown to the customer and sent to the provider as its transaction id (ABA PayWay: max 20).
    reference VARCHAR(20) NOT NULL,
    customer_type VARCHAR(10) NOT NULL,
    -- NULL after the family is erased: the financial facts stay, the link to the person does not.
    family_id UUID,
    school_id UUID,
    created_by_account_id UUID,
    plan_id UUID NOT NULL,
    plan_code VARCHAR(60) NOT NULL,
    plan_name VARCHAR(120) NOT NULL,
    -- The plan row's optimistic version when checkout began: which edit of the plan was offered.
    plan_version BIGINT NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    billing_interval VARCHAR(10) NOT NULL,
    -- The plan's limits as offered ({"CHILDREN_MAX": 3, ...}; null = unlimited).
    limits_snapshot JSONB NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_reference VARCHAR(200),
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    -- The access this payment gave, once it succeeded (a refund takes exactly this back).
    period_start TIMESTAMPTZ,
    period_end TIMESTAMPTZ,
    failure_reason VARCHAR(300),
    -- What the customer says they paid with (a transfer reference), for the reviewer; not proof.
    customer_note VARCHAR(300),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_payment_orders_reference UNIQUE (reference),
    CONSTRAINT uq_payment_orders_idempotency UNIQUE (created_by_account_id, idempotency_key),
    CONSTRAINT fk_payment_orders_family FOREIGN KEY (family_id) REFERENCES families (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_orders_school FOREIGN KEY (school_id) REFERENCES schools (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_orders_created_by FOREIGN KEY (created_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_orders_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id) ON DELETE RESTRICT,
    CONSTRAINT ck_payment_orders_customer CHECK (
        (customer_type = 'FAMILY' AND school_id IS NULL)
        OR (customer_type = 'SCHOOL' AND school_id IS NOT NULL AND family_id IS NULL)),
    CONSTRAINT ck_payment_orders_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_payment_orders_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_orders_interval CHECK (billing_interval IN ('MONTH', 'YEAR')),
    CONSTRAINT ck_payment_orders_provider CHECK (provider IN ('DEV_SIMULATOR', 'MANUAL', 'ABA_PAYWAY')),
    CONSTRAINT ck_payment_orders_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED', 'REFUNDED')),
    -- A succeeded (or refunded) order always knows when it completed and what access it gave.
    CONSTRAINT ck_payment_orders_completed CHECK (
        status NOT IN ('SUCCEEDED', 'REFUNDED') OR (completed_at IS NOT NULL AND period_start IS NOT NULL AND period_end IS NOT NULL)),
    CONSTRAINT ck_payment_orders_version CHECK (version >= 0)
);

-- At most one payment in progress per customer: a second checkout can't race the first.
CREATE UNIQUE INDEX uq_payment_orders_pending_family ON payment_orders (family_id) WHERE status = 'PENDING' AND family_id IS NOT NULL;
CREATE UNIQUE INDEX uq_payment_orders_pending_school ON payment_orders (school_id) WHERE status = 'PENDING' AND school_id IS NOT NULL;
CREATE INDEX ix_payment_orders_status ON payment_orders (status, created_at);
CREATE INDEX ix_payment_orders_family ON payment_orders (family_id, created_at) WHERE family_id IS NOT NULL;
CREATE INDEX ix_payment_orders_school ON payment_orders (school_id, created_at) WHERE school_id IS NOT NULL;

CREATE TRIGGER trg_payment_orders_updated_at
    BEFORE UPDATE ON payment_orders
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- The ledger: append-only. A provider event is recorded once (its id is unique per order), so a
-- repeated callback is recognised and changes nothing.
CREATE TABLE payment_events (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20),
    actor_account_id UUID,
    provider_event_id VARCHAR(200),
    -- What happened, in words and safe values only (never secrets or card data).
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_payment_events_order FOREIGN KEY (order_id) REFERENCES payment_orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_events_actor FOREIGN KEY (actor_account_id) REFERENCES accounts (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_payment_events_provider_event ON payment_events (order_id, provider_event_id) WHERE provider_event_id IS NOT NULL;
CREATE INDEX ix_payment_events_order ON payment_events (order_id, created_at);

-- The ledger is never rewritten - except that erasing an account may clear its name from it (the
-- same session flag as V51's evidence tables); rows are never deleted.
CREATE FUNCTION payment_events_append_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND current_setting('bookeyond.erasure', true) = 'on' THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'payment_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payment_events_append_only
    BEFORE UPDATE OR DELETE ON payment_events
    FOR EACH ROW
    EXECUTE FUNCTION payment_events_append_only();

-- A subscription that came from a payment names the order (V42 already requires provider and
-- reference for source PROVIDER); the school table gets the same rule.
ALTER TABLE school_subscriptions
    ADD CONSTRAINT ck_school_subscriptions_provider CHECK (source <> 'PROVIDER' OR (provider IS NOT NULL AND provider_reference IS NOT NULL));
