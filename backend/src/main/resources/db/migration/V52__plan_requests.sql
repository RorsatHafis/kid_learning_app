-- A family asks for one of the plans it is offered (Y11). There is no payment provider yet
-- (PD-09): an administrator answers the request, granting the plan through the existing grant
-- (which resolves the request) or declining it. At most one open request per family.
CREATE TABLE plan_requests (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    requested_by_account_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by_account_id UUID,
    resolution_note VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_plan_requests_family FOREIGN KEY (family_id) REFERENCES families (id) ON DELETE RESTRICT,
    CONSTRAINT fk_plan_requests_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans (id) ON DELETE RESTRICT,
    CONSTRAINT fk_plan_requests_requested_by FOREIGN KEY (requested_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_plan_requests_resolved_by FOREIGN KEY (resolved_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_plan_requests_status CHECK (status IN ('OPEN', 'GRANTED', 'DECLINED', 'WITHDRAWN')),
    CONSTRAINT ck_plan_requests_resolution CHECK ((status = 'OPEN') = (resolved_at IS NULL)),
    CONSTRAINT ck_plan_requests_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_plan_requests_open_per_family ON plan_requests (family_id) WHERE status = 'OPEN';
CREATE INDEX ix_plan_requests_open ON plan_requests (requested_at) WHERE status = 'OPEN';

CREATE TRIGGER trg_plan_requests_updated_at
    BEFORE UPDATE ON plan_requests
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
