CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    idempotency_record_id UUID,
    actor_id UUID,
    actor_type VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id UUID,
    correlation_id VARCHAR(128) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_audit_events_idempotency_record
        FOREIGN KEY (idempotency_record_id)
        REFERENCES idempotency_records (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_audit_events_actor_type
        CHECK (char_length(btrim(actor_type)) > 0),
    CONSTRAINT ck_audit_events_action
        CHECK (char_length(btrim(action)) > 0),
    CONSTRAINT ck_audit_events_resource_type
        CHECK (char_length(btrim(resource_type)) > 0),
    CONSTRAINT ck_audit_events_correlation_id
        CHECK (char_length(btrim(correlation_id)) > 0)
);

CREATE INDEX ix_audit_events_actor_occurred_at
    ON audit_events (actor_id, occurred_at DESC)
    WHERE actor_id IS NOT NULL;

CREATE INDEX ix_audit_events_resource_occurred_at
    ON audit_events (resource_type, resource_id, occurred_at DESC)
    WHERE resource_id IS NOT NULL;

CREATE INDEX ix_audit_events_idempotency_record
    ON audit_events (idempotency_record_id)
    WHERE idempotency_record_id IS NOT NULL;

CREATE FUNCTION prevent_audit_event_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit events are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_immutable
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_event_mutation();
