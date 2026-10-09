-- A second person's review before platform content is published (Y14, PD-20). Used only when
-- CONTENT_REVIEW_REQUIRED is on: an author asks for a review of a draft version; another
-- administrator approves it (which publishes it) or asks for changes. The fingerprint records
-- the draft as it was when the review was asked, so a draft edited since can't be approved.
CREATE TABLE content_reviews (
    id UUID PRIMARY KEY,
    content_kind VARCHAR(20) NOT NULL,
    version_id UUID NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    requested_by_account_id UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    decided_by_account_id UUID,
    decided_at TIMESTAMPTZ,
    note VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_content_reviews_requested_by FOREIGN KEY (requested_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_content_reviews_decided_by FOREIGN KEY (decided_by_account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_content_reviews_kind CHECK (content_kind IN ('QUESTION', 'ACTIVITY', 'CURRICULUM')),
    CONSTRAINT ck_content_reviews_status CHECK (status IN ('OPEN', 'APPROVED', 'CHANGES_REQUESTED', 'WITHDRAWN')),
    CONSTRAINT ck_content_reviews_decision CHECK ((status = 'OPEN') = (decided_at IS NULL)),
    CONSTRAINT ck_content_reviews_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uq_content_reviews_open_per_version ON content_reviews (version_id) WHERE status = 'OPEN';
CREATE INDEX ix_content_reviews_version ON content_reviews (version_id, requested_at DESC);
CREATE INDEX ix_content_reviews_open ON content_reviews (requested_at) WHERE status = 'OPEN';

CREATE TRIGGER trg_content_reviews_updated_at
    BEFORE UPDATE ON content_reviews
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
