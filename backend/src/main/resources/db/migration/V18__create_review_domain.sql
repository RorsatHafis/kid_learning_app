
CREATE TABLE review_items (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    skill_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_review_items_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_items_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT uq_review_items_child_skill UNIQUE (child_id, skill_id),
    CONSTRAINT ck_review_items_status CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT ck_review_items_version CHECK (version >= 0)
);

CREATE INDEX ix_review_items_child_id ON review_items (child_id);
CREATE INDEX ix_review_items_skill_id ON review_items (skill_id);

CREATE TRIGGER trg_review_items_updated_at
    BEFORE UPDATE ON review_items
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE review_schedules (
    id UUID PRIMARY KEY,
    review_item_id UUID NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    interval_days INT NOT NULL DEFAULT 1,
    ease_factor NUMERIC(4, 2) NOT NULL DEFAULT 2.5,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_review_schedules_review_item
        FOREIGN KEY (review_item_id) REFERENCES review_items (id) ON DELETE RESTRICT,
    CONSTRAINT uq_review_schedules_review_item UNIQUE (review_item_id),
    CONSTRAINT ck_review_schedules_interval_days CHECK (interval_days > 0),
    CONSTRAINT ck_review_schedules_ease_factor CHECK (ease_factor > 0),
    CONSTRAINT ck_review_schedules_version CHECK (version >= 0)
);

CREATE INDEX ix_review_schedules_due_at ON review_schedules (due_at);

CREATE TRIGGER trg_review_schedules_updated_at
    BEFORE UPDATE ON review_schedules
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE review_attempts (
    id UUID PRIMARY KEY,
    review_item_id UUID NOT NULL,
    activity_attempt_id UUID NOT NULL,
    was_successful BOOLEAN NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_review_attempts_review_item
        FOREIGN KEY (review_item_id) REFERENCES review_items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_review_attempts_activity_attempt
        FOREIGN KEY (activity_attempt_id) REFERENCES activity_attempts (id) ON DELETE RESTRICT,
    CONSTRAINT uq_review_attempts_activity_attempt UNIQUE (activity_attempt_id)
);

CREATE INDEX ix_review_attempts_review_item_id ON review_attempts (review_item_id);

CREATE TRIGGER trg_review_attempts_immutable
    BEFORE UPDATE OR DELETE ON review_attempts
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();
