CREATE TABLE activity_items (
    id UUID PRIMARY KEY,
    activity_version_id UUID NOT NULL,
    question_version_id UUID NOT NULL,
    sequence_order INT NOT NULL,
    points NUMERIC(5, 2) NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_activity_items_activity_version
        FOREIGN KEY (activity_version_id) REFERENCES activity_versions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_items_question_version
        FOREIGN KEY (question_version_id) REFERENCES question_versions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_activity_items_version_order UNIQUE (activity_version_id, sequence_order),
    CONSTRAINT ck_activity_items_points CHECK (points > 0)
);

CREATE INDEX ix_activity_items_activity_version_id ON activity_items (activity_version_id);
CREATE INDEX ix_activity_items_question_version_id ON activity_items (question_version_id);
