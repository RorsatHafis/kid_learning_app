-- Owner decision 2026-11-11 (D-20): one retry with feedback after a wrong answer; a right second try is weaker mastery
-- evidence than a right first try. answer_records stays append-only: the first answer remains the record (its
-- correctness is what the activity's score counts), and whether a retry was offered is fixed when it is written.
-- Existing rows were never offered one (their evidence was recorded when they were answered).
ALTER TABLE answer_records ADD COLUMN retry_offered BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE answer_records ADD CONSTRAINT ck_answer_records_retry_after_miss CHECK (NOT (retry_offered AND is_correct));

-- What happened after the offer is its own append-only row: the second answer (RETRIED), or the child asking to see
-- the answer / moving on without one (DECLINED). One per offered answer. The question's evidence is recorded once,
-- when this row is written.
CREATE TABLE answer_retries (
    id                 UUID PRIMARY KEY,
    answer_record_id   UUID        NOT NULL,
    outcome            VARCHAR(16) NOT NULL,
    submitted_answer   TEXT,
    is_correct         BOOLEAN,
    hint_used          BOOLEAN     NOT NULL DEFAULT FALSE,
    time_spent_seconds INT,
    occurred_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_answer_retries_answer FOREIGN KEY (answer_record_id) REFERENCES answer_records (id) ON DELETE RESTRICT,
    CONSTRAINT uq_answer_retries_answer UNIQUE (answer_record_id),
    CONSTRAINT ck_answer_retries_outcome CHECK (outcome IN ('RETRIED', 'DECLINED')),
    CONSTRAINT ck_answer_retries_shape CHECK ((outcome = 'RETRIED') = (submitted_answer IS NOT NULL AND is_correct IS NOT NULL)),
    CONSTRAINT ck_answer_retries_time CHECK (time_spent_seconds IS NULL OR time_spent_seconds >= 0)
);

CREATE TRIGGER trg_answer_retries_immutable
    BEFORE UPDATE OR DELETE ON answer_retries
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();
