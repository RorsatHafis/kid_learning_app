
CREATE TABLE child_preferences (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    stated_daily_learning_minutes INT,
    learning_style_signals JSONB NOT NULL DEFAULT '{}',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_child_preferences_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT uq_child_preferences_child UNIQUE (child_id),
    CONSTRAINT ck_child_preferences_stated_minutes
        CHECK (stated_daily_learning_minutes IS NULL OR stated_daily_learning_minutes > 0),
    CONSTRAINT ck_child_preferences_version CHECK (version >= 0)
);

CREATE INDEX ix_child_preferences_child_id ON child_preferences (child_id);

CREATE TRIGGER trg_child_preferences_updated_at
    BEFORE UPDATE ON child_preferences
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE child_preferred_subjects (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_child_preferred_subjects_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT fk_child_preferred_subjects_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE RESTRICT,
    CONSTRAINT uq_child_preferred_subjects_pair UNIQUE (child_id, subject_id)
);

CREATE INDEX ix_child_preferred_subjects_child_id ON child_preferred_subjects (child_id);
CREATE INDEX ix_child_preferred_subjects_subject_id ON child_preferred_subjects (subject_id);
