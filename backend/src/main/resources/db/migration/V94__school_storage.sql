-- M11 (D-06, owner decision 2026-11-11: hybrid storage). Bookeyond-hosted storage stays the default; a school may
-- connect its own S3-compatible storage (and pays its provider directly). A connection is never edited: connecting
-- again makes a new one, so every file stays readable from the storage it was written to.
CREATE TABLE storage_connections (
    id                 UUID PRIMARY KEY,
    school_id          UUID         NOT NULL REFERENCES schools (id) ON DELETE RESTRICT,
    endpoint           VARCHAR(300) NOT NULL,
    region             VARCHAR(64)  NOT NULL,
    bucket             VARCHAR(63)  NOT NULL,
    path_style         BOOLEAN      NOT NULL,
    access_key_id      VARCHAR(128) NOT NULL,
    -- AES-256-GCM, the connection's id as associated data (StorageSeal); never returned.
    access_key_sealed  TEXT         NOT NULL,
    connected_by       UUID         REFERENCES accounts (id) ON DELETE SET NULL,
    verified_at        TIMESTAMPTZ  NOT NULL,
    last_checked_at    TIMESTAMPTZ,
    last_check_ok      BOOLEAN,
    last_check_error   VARCHAR(300),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_storage_connections_endpoint CHECK (endpoint ~ '^https?://[^/?#@]+$'),
    CONSTRAINT ck_storage_connections_bucket CHECK (bucket ~ '^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$')
);
CREATE INDEX ix_storage_connections_school ON storage_connections (school_id, created_at DESC);

-- The school's current choice: NULL = Bookeyond-hosted (the default).
ALTER TABLE schools ADD COLUMN storage_connection_id UUID REFERENCES storage_connections (id) ON DELETE RESTRICT;

-- Where each file was written: NULL = Bookeyond-hosted.
ALTER TABLE content_assets ADD COLUMN storage_connection_id UUID REFERENCES storage_connections (id) ON DELETE RESTRICT;
CREATE INDEX ix_content_assets_storage_connection ON content_assets (storage_connection_id) WHERE storage_connection_id IS NOT NULL;
