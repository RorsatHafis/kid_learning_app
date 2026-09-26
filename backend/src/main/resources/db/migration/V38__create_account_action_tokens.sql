-- Single-use, expiring tokens for actions proven by access to the account's mailbox:
-- confirming the email address and resetting a forgotten password (CLAUDE.md §11).
-- Only the SHA-256 hex digest of the raw token is stored; the raw token exists only in
-- the email that carries it.
CREATE TABLE account_action_tokens (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    requested_ip VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_account_action_tokens_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT uq_account_action_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_account_action_tokens_purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_account_action_tokens_expiry CHECK (expires_at > issued_at),
    -- A token ends at most one way: used, or superseded/withdrawn.
    CONSTRAINT ck_account_action_tokens_single_end CHECK (consumed_at IS NULL OR invalidated_at IS NULL),
    CONSTRAINT ck_account_action_tokens_version CHECK (version >= 0)
);

-- Resend limits count an account's recent tokens per purpose.
CREATE INDEX ix_account_action_tokens_account_purpose ON account_action_tokens (account_id, purpose, issued_at);

CREATE TRIGGER trg_account_action_tokens_updated_at
    BEFORE UPDATE ON account_action_tokens
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
