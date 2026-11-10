-- Owner requirement 2026-11-10: an email is verified BEFORE an account exists. One row per verification request:
-- the emailed link (stored only as a SHA-256 hash), and - once the link is opened - a short-lived registration grant
-- (hash only) that the registration consumes in the same transaction that creates the account. No account, family
-- or membership is created until then; rows hold only the address the person typed and timestamps.
CREATE TABLE registration_verifications (
    id               UUID PRIMARY KEY,
    normalized_email VARCHAR(320) NOT NULL,
    link_token_hash  CHAR(64)     NOT NULL,
    link_expires_at  TIMESTAMPTZ  NOT NULL,
    link_used_at     TIMESTAMPTZ,
    grant_hash       CHAR(64),
    grant_expires_at TIMESTAMPTZ,
    grant_used_at    TIMESTAMPTZ,
    requested_ip     VARCHAR(64),
    created_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_registration_verifications_link UNIQUE (link_token_hash),
    CONSTRAINT uq_registration_verifications_grant UNIQUE (grant_hash),
    CONSTRAINT ck_registration_verifications_email CHECK (normalized_email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    CONSTRAINT ck_registration_verifications_grant CHECK ((grant_hash IS NULL) = (grant_expires_at IS NULL)),
    CONSTRAINT ck_registration_verifications_order CHECK (link_expires_at > created_at)
);

-- Cooldown and hourly-cap lookups per address.
CREATE INDEX ix_registration_verifications_email_created ON registration_verifications (normalized_email, created_at DESC);
