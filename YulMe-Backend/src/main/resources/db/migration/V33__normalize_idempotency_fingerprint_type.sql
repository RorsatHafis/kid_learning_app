ALTER TABLE idempotency_records
    ALTER COLUMN request_fingerprint TYPE VARCHAR(64);
