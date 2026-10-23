-- M9a (product improvement program): a second factor for administrators - a time-based one-time code (TOTP,
-- RFC 6238) from an authenticator app - required with the password before actions that move money or access
-- (the step-up of M8). The secret is stored encrypted; recovery codes only as digests.

CREATE TABLE account_second_factors (
    account_id UUID PRIMARY KEY,
    -- AES-GCM ciphertext of the TOTP secret (iv || ciphertext), base64; never returned after setup.
    secret_ciphertext VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at TIMESTAMPTZ,
    -- The last time step a code was accepted for: a code can't be used twice.
    last_used_step BIGINT,
    -- Wrong codes in a row. Counted here, not with the sign-in failures, because the password that comes with each
    -- code resets those: at the limit the account is locked as for a wrong password.
    failed_attempts INT NOT NULL DEFAULT 0,

    CONSTRAINT fk_account_second_factors_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_account_second_factors_status CHECK (status IN ('PENDING', 'ACTIVE')),
    CONSTRAINT ck_account_second_factors_active CHECK (status = 'PENDING' OR activated_at IS NOT NULL),
    CONSTRAINT ck_account_second_factors_failed CHECK (failed_attempts >= 0)
);

-- One-time codes for a lost authenticator, given once when it is turned on.
CREATE TABLE account_recovery_codes (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    code_hash CHAR(64) NOT NULL,
    used_at TIMESTAMPTZ,

    CONSTRAINT fk_account_recovery_codes_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT
);
CREATE INDEX ix_account_recovery_codes_account ON account_recovery_codes (account_id);
