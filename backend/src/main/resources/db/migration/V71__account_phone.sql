-- M9b (product improvement program): a phone number on an account, verified by a 6-digit code sent by SMS.
-- Numbers are stored normalized (E.164, e.g. +85512345678). A number can be verified on one account only; an
-- unverified one is only a pending choice of its own account. Codes are kept only as digests, expire after 10
-- minutes, allow 5 tries and are rate-limited per account and per number (PhoneVerificationService).

ALTER TABLE accounts
    ADD COLUMN phone_e164 VARCHAR(16),
    ADD COLUMN phone_verified_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_accounts_phone_e164 CHECK (phone_e164 IS NULL OR phone_e164 ~ '^\+[1-9][0-9]{6,14}$'),
    ADD CONSTRAINT ck_accounts_phone_verified CHECK (phone_verified_at IS NULL OR phone_e164 IS NOT NULL);

CREATE UNIQUE INDEX uq_accounts_verified_phone ON accounts (phone_e164) WHERE phone_verified_at IS NOT NULL;

CREATE TABLE phone_verifications (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    phone_e164 VARCHAR(16) NOT NULL,
    -- SHA-256 of the challenge id and the code: the code itself is never stored.
    code_hash CHAR(64) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    -- Ended one way at most: confirmed, or replaced by a newer code.
    confirmed_at TIMESTAMPTZ,
    superseded_at TIMESTAMPTZ,
    -- Whether an SMS was actually handed to the sender (not, e.g., for a number verified on another account).
    sent BOOLEAN NOT NULL,

    CONSTRAINT fk_phone_verifications_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_phone_verifications_phone CHECK (phone_e164 ~ '^\+[1-9][0-9]{6,14}$'),
    CONSTRAINT ck_phone_verifications_expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_phone_verifications_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_phone_verifications_one_end CHECK (confirmed_at IS NULL OR superseded_at IS NULL)
);

-- Limits count recent codes per account and per number.
CREATE INDEX ix_phone_verifications_account ON phone_verifications (account_id, issued_at);
CREATE INDEX ix_phone_verifications_phone ON phone_verifications (phone_e164, issued_at);
