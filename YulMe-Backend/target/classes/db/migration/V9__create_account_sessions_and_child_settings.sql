
CREATE TABLE account_sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    refresh_token_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    rotated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    replaced_by_session_id UUID,
    user_agent VARCHAR(500),
    ip_address VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_account_sessions_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_sessions_replaced_by
        FOREIGN KEY (replaced_by_session_id) REFERENCES account_sessions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_account_sessions_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT ck_account_sessions_status CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_account_sessions_expires_after_issued CHECK (expires_at > issued_at),
    CONSTRAINT ck_account_sessions_rotated_consistency CHECK (
        (status = 'ROTATED') = (replaced_by_session_id IS NOT NULL)
    ),
    CONSTRAINT ck_account_sessions_version CHECK (version >= 0)
);

CREATE INDEX ix_account_sessions_account_id ON account_sessions (account_id);
CREATE INDEX ix_account_sessions_expires_at ON account_sessions (expires_at);

CREATE TRIGGER trg_account_sessions_updated_at
    BEFORE UPDATE ON account_sessions
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE child_settings (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    daily_time_limit_minutes INT,
    content_restrictions JSONB NOT NULL DEFAULT '{}',
    notifications_enabled BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_child_settings_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT uq_child_settings_child UNIQUE (child_id),
    CONSTRAINT ck_child_settings_daily_time_limit
        CHECK (daily_time_limit_minutes IS NULL OR daily_time_limit_minutes > 0),
    CONSTRAINT ck_child_settings_version CHECK (version >= 0)
);

CREATE INDEX ix_child_settings_child_id ON child_settings (child_id);

CREATE TRIGGER trg_child_settings_updated_at
    BEFORE UPDATE ON child_settings
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
