
CREATE TABLE enrollments (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    curriculum_version_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_enrollments_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT fk_enrollments_curriculum_version
        FOREIGN KEY (curriculum_version_id) REFERENCES curriculum_versions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_enrollments_child_curriculum_version UNIQUE (child_id, curriculum_version_id),
    CONSTRAINT ck_enrollments_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'PAUSED', 'WITHDRAWN')),
    CONSTRAINT ck_enrollments_completed_at CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL) OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_enrollments_version CHECK (version >= 0)
);

CREATE INDEX ix_enrollments_child_id ON enrollments (child_id);
CREATE INDEX ix_enrollments_curriculum_version_id ON enrollments (curriculum_version_id);

CREATE TRIGGER trg_enrollments_updated_at
    BEFORE UPDATE ON enrollments
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE learning_paths (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_learning_paths_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments (id) ON DELETE RESTRICT,
    CONSTRAINT uq_learning_paths_enrollment UNIQUE (enrollment_id),
    CONSTRAINT ck_learning_paths_status CHECK (status IN ('ACTIVE', 'COMPLETED')),
    CONSTRAINT ck_learning_paths_version CHECK (version >= 0)
);

CREATE TRIGGER trg_learning_paths_updated_at
    BEFORE UPDATE ON learning_paths
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE learning_path_items (
    id UUID PRIMARY KEY,
    learning_path_id UUID NOT NULL,
    activity_version_id UUID NOT NULL,
    sequence_order INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    source VARCHAR(30) NOT NULL DEFAULT 'CURRICULUM',
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_learning_path_items_path
        FOREIGN KEY (learning_path_id) REFERENCES learning_paths (id) ON DELETE RESTRICT,
    CONSTRAINT fk_learning_path_items_activity_version
        FOREIGN KEY (activity_version_id) REFERENCES activity_versions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_learning_path_items_path_order UNIQUE (learning_path_id, sequence_order),
    CONSTRAINT ck_learning_path_items_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED')),
    CONSTRAINT ck_learning_path_items_source CHECK (source IN ('CURRICULUM', 'ADAPTIVE', 'REVIEW')),
    CONSTRAINT ck_learning_path_items_completed_at CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL) OR (status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_learning_path_items_version CHECK (version >= 0)
);

CREATE INDEX ix_learning_path_items_learning_path_id ON learning_path_items (learning_path_id);
CREATE INDEX ix_learning_path_items_activity_version_id ON learning_path_items (activity_version_id);

CREATE TRIGGER trg_learning_path_items_updated_at
    BEFORE UPDATE ON learning_path_items
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE learning_sessions (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_learning_sessions_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT ck_learning_sessions_ended_after_started CHECK (ended_at IS NULL OR ended_at >= started_at),
    CONSTRAINT ck_learning_sessions_version CHECK (version >= 0)
);

CREATE INDEX ix_learning_sessions_child_id ON learning_sessions (child_id);

CREATE TRIGGER trg_learning_sessions_updated_at
    BEFORE UPDATE ON learning_sessions
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE activity_attempts (
    id UUID PRIMARY KEY,
    child_id UUID NOT NULL,
    activity_version_id UUID NOT NULL,
    learning_path_item_id UUID,
    learning_session_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    score NUMERIC(5, 2),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_activity_attempts_child FOREIGN KEY (child_id) REFERENCES children (id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_attempts_activity_version
        FOREIGN KEY (activity_version_id) REFERENCES activity_versions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_attempts_learning_path_item
        FOREIGN KEY (learning_path_item_id) REFERENCES learning_path_items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_attempts_learning_session
        FOREIGN KEY (learning_session_id) REFERENCES learning_sessions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_activity_attempts_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT ck_activity_attempts_completed_at CHECK (
        (status = 'IN_PROGRESS' AND completed_at IS NULL) OR (status <> 'IN_PROGRESS' AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_activity_attempts_score CHECK (score IS NULL OR (score >= 0 AND score <= 100)),
    CONSTRAINT ck_activity_attempts_version CHECK (version >= 0)
);

CREATE INDEX ix_activity_attempts_child_id ON activity_attempts (child_id);
CREATE INDEX ix_activity_attempts_activity_version_id ON activity_attempts (activity_version_id);
CREATE INDEX ix_activity_attempts_learning_path_item_id ON activity_attempts (learning_path_item_id);
CREATE INDEX ix_activity_attempts_learning_session_id ON activity_attempts (learning_session_id);

CREATE TRIGGER trg_activity_attempts_updated_at
    BEFORE UPDATE ON activity_attempts
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TABLE answer_records (
    id UUID PRIMARY KEY,
    activity_attempt_id UUID NOT NULL,
    question_version_id UUID NOT NULL,
    submitted_answer TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    time_spent_seconds INT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_answer_records_attempt
        FOREIGN KEY (activity_attempt_id) REFERENCES activity_attempts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_answer_records_question_version
        FOREIGN KEY (question_version_id) REFERENCES question_versions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_answer_records_time_spent CHECK (time_spent_seconds IS NULL OR time_spent_seconds >= 0)
);

CREATE INDEX ix_answer_records_activity_attempt_id ON answer_records (activity_attempt_id);
CREATE INDEX ix_answer_records_question_version_id ON answer_records (question_version_id);

CREATE TRIGGER trg_answer_records_immutable
    BEFORE UPDATE OR DELETE ON answer_records
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();

CREATE TABLE activity_events (
    id UUID PRIMARY KEY,
    activity_attempt_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_activity_events_attempt
        FOREIGN KEY (activity_attempt_id) REFERENCES activity_attempts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_activity_events_type_not_blank CHECK (char_length(btrim(event_type)) > 0)
);

CREATE INDEX ix_activity_events_activity_attempt_id ON activity_events (activity_attempt_id, occurred_at);

CREATE TRIGGER trg_activity_events_immutable
    BEFORE UPDATE OR DELETE ON activity_events
    FOR EACH ROW
    EXECUTE FUNCTION prevent_row_mutation();
