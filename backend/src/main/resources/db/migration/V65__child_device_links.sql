-- M10a (product improvement program): a parent opens one child's learning space on another device (a tablet, a
-- shared phone) without signing that device in as themselves. The parent makes a short-lived code; the other device
-- enters it; the parent approves on their own device; the other device then gets a sign-in scoped to that child only.

-- The sign-in a child device holds: the parent's account (whose family gives access), limited to one child. Null for
-- every ordinary sign-in. Carried across refresh rotation, so the scope never widens.
ALTER TABLE account_sessions ADD COLUMN child_id UUID;
ALTER TABLE account_sessions
    ADD CONSTRAINT fk_account_sessions_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT;
CREATE INDEX ix_account_sessions_child ON account_sessions (child_id) WHERE child_id IS NOT NULL;

-- One code a parent made for one child. Only a digest of the code and of the waiting device's secret is kept.
CREATE TABLE child_device_codes (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    created_by_account_id UUID NOT NULL,
    code_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claim_secret_hash CHAR(64),
    claim_user_agent VARCHAR(500),
    claimed_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    session_family_id UUID,

    CONSTRAINT fk_child_device_codes_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT ck_child_device_codes_status CHECK (status IN ('OPEN', 'CLAIMED', 'APPROVED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_child_device_codes_claimed CHECK (status = 'OPEN' OR status = 'CANCELLED' OR claim_secret_hash IS NOT NULL)
);

-- A code is found by its digest while it can still be entered; two open codes never share one.
CREATE UNIQUE INDEX uq_child_device_codes_open_hash ON child_device_codes (code_hash) WHERE status = 'OPEN';
CREATE INDEX ix_child_device_codes_child ON child_device_codes (child_id);
