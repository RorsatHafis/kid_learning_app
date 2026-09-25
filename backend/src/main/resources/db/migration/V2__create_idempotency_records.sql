CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    operation VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_idempotency_records_actor_operation_key
        UNIQUE (actor_id, operation, idempotency_key),
    CONSTRAINT ck_idempotency_records_fingerprint
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_idempotency_records_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_idempotency_records_completion
        CHECK (
            (status = 'IN_PROGRESS' AND completed_at IS NULL)
            OR (status IN ('COMPLETED', 'FAILED') AND completed_at IS NOT NULL)
        ),
    CONSTRAINT ck_idempotency_records_expiration
        CHECK (expires_at > created_at),
    CONSTRAINT ck_idempotency_records_version
        CHECK (version >= 0)
);

CREATE INDEX ix_idempotency_records_expiration
    ON idempotency_records (expires_at);

CREATE TRIGGER trg_idempotency_records_updated_at
    BEFORE UPDATE ON idempotency_records
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
