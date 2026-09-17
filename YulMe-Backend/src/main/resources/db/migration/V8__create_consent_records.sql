CREATE TABLE consent_records (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    granted_by_account_id UUID NOT NULL,
    consent_type VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_consent_records_child
        FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT fk_consent_records_account
        FOREIGN KEY (granted_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_consent_records_type
        CHECK (consent_type IN ('DATA_COLLECTION', 'TERMS_OF_SERVICE')),
    CONSTRAINT ck_consent_records_action
        CHECK (action IN ('GRANTED', 'REVOKED'))
);

CREATE INDEX ix_consent_records_child_type_occurred
    ON consent_records (child_id, consent_type, occurred_at DESC);

CREATE FUNCTION prevent_row_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION '% rows are immutable', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_consent_records_immutable
    BEFORE UPDATE OR DELETE ON consent_records
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();
