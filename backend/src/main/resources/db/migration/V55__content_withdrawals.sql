-- Taking a published version down (Y16, PD-04's mechanism): a wrong answer key or harmful content
-- must stop reaching children at once. A withdrawn question version also takes down every activity
-- version that contains it. Nothing already recorded changes: answers and evidence stay; what to
-- do about them is the owner's decision. Restoring lets the version be served again.
CREATE TABLE content_withdrawals (
    id UUID PRIMARY KEY,
    content_kind VARCHAR(20) NOT NULL,
    version_id UUID NOT NULL,
    reason VARCHAR(500) NOT NULL,
    withdrawn_by_account_id UUID NOT NULL,
    withdrawn_at TIMESTAMPTZ NOT NULL,
    restored_by_account_id UUID,
    restored_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_content_withdrawals_by FOREIGN KEY (withdrawn_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_content_withdrawals_restored_by FOREIGN KEY (restored_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_content_withdrawals_kind CHECK (content_kind IN ('QUESTION', 'ACTIVITY')),
    CONSTRAINT ck_content_withdrawals_reason CHECK (char_length(btrim(reason)) > 0),
    CONSTRAINT ck_content_withdrawals_restore CHECK ((restored_at IS NULL) = (restored_by_account_id IS NULL)),
    CONSTRAINT ck_content_withdrawals_version CHECK (version >= 0)
);

-- At most one withdrawal in force per version; selection asks "is this version withdrawn?" constantly.
CREATE UNIQUE INDEX uq_content_withdrawals_active ON content_withdrawals (version_id) WHERE restored_at IS NULL;

CREATE TRIGGER trg_content_withdrawals_updated_at
    BEFORE UPDATE ON content_withdrawals
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
